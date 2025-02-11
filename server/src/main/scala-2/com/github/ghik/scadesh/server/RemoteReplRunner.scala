package com.github.ghik.recons
package server

import com.github.ghik.recons.core.{CompilerCommand, CompleteResult, ParseResult}
import com.github.ghik.recons.server.utils.ShellExtensions
import org.jline.reader.{EOFError, SyntaxError}

import java.io.PrintWriter
import java.net.Socket
import scala.tools.nsc.interpreter.jline.Reader.{ScalaParsedLine, ScalaParser}
import scala.tools.nsc.interpreter.shell.{Accumulator, ILoop, InteractiveReader, ShellConfig}
import scala.tools.nsc.{GenericRunnerSettings, Settings}
import scala.util.control.NonFatal

object RemoteReplRunner {
  def run(
    args: Array[String],
    socket: Socket,
    replConfig: ReplConfig,
  ): Unit = {
    val comm = new ServerCommunicator(socket)
    val out = new PrintWriter(new CommunicatorOutputStream(comm))
    val settings = new GenericRunnerSettings(s => if (s.nonEmpty) out.println(s))
    settings.processArguments(args.toList, processAll = false)
    System.setProperty("scala.color", "true")
    val shellConfig: ShellConfig = ShellConfig(settings)
    new RemoteReplRunner(comm, shellConfig, out, replConfig).run(settings)
  }
}
class RemoteReplRunner(
  comm: ServerCommunicator,
  shellConfig: ShellConfig,
  out: PrintWriter,
  replConfig: ReplConfig,
) extends ILoop(shellConfig, out = new PrintWriter(out)) {
  private var initialized = false

  override def welcome: String = replConfig.welcome

  override lazy val prompt: String =
    shellConfig.encolor("\n" + replConfig.prompt)

  override def createInterpreter(interpreterSettings: Settings): Unit = {
    super.createInterpreter(interpreterSettings)

    if (!initialized) {
      try {
        // force initialization of `lazy val defaultIn`
        val defaultInGetter = classOf[ILoop].getDeclaredMethod("defaultIn")
        defaultInGetter.setAccessible(true)
        val prevReader = defaultInGetter.invoke(this).asInstanceOf[InteractiveReader]
        // close the default terminal
        prevReader.close()
      } catch {
        case NonFatal(_) => //ignore
      }

      // replace defaultIn with RemoteReader
      val deafultInField = classOf[ILoop].getDeclaredField("defaultIn")
      deafultInField.setAccessible(true)
      val accumulator = new Accumulator
      // must do this after `intp` is set by super call, because `completion` needs it
      val completer = completion(accumulator)
      deafultInField.set(this, new RemoteReader(comm, accumulator, completer, replConfig))

      val parser = new ScalaParser(intp)

      comm.setCommandHandler(new comm.CommandHandler {
        def handleCommand[T](cmd: CompilerCommand[T]): T = cmd match {
          case CompilerCommand.Parse(line, cursor, context) =>
            try ParseResult.Success(parser.parse(line, cursor, context).asInstanceOf[ScalaParsedLine]) catch {
              case _: EOFError => ParseResult.EOFError
              case _: SyntaxError => ParseResult.SyntaxError
            }

          case CompilerCommand.Complete(buffer, cursor, filter) =>
            CompleteResult(completer.complete(buffer, cursor, filter))
        }
      })

      if (replConfig.useExtensions) {
        intp.bind(
          ShellExtensions.BindingName,
          classOf[ShellExtensions].getName,
          new ShellExtensions(new CommunicatorPrintStream(comm)),
        )
      }
      replConfig.bindings.foreach {
        case (name, ReplBinding(staticType, value)) =>
          intp.bind(name, staticType, value)
      }

      initialized = true
    }
  }
}
