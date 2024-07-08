package com.github.ghik.scadesh
package server

import com.github.ghik.scadesh.core.{CompilerCommand, CompleteResult, ParseResult}
import com.github.ghik.scadesh.server.utils.ShellExtensions
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
    bindings: Map[String, ReplBinding],
    initCode: String,
  ): Unit = {
    val comm = new ServerCommunicator(socket)
    val out = new PrintWriter(new CommunicatorOutputStream(comm))
    val settings = new GenericRunnerSettings(s => if (s.nonEmpty) out.println(s))
    settings.processArguments(args.toList, processAll = false)
    System.setProperty("scala.color", "true")
    val config: ShellConfig = ShellConfig(settings)
    new RemoteReplRunner(comm, config, out, bindings, initCode).run(settings)
  }
}
class RemoteReplRunner(
  comm: ServerCommunicator,
  config: ShellConfig,
  out: PrintWriter,
  bindings: Map[String, ReplBinding],
  initCode: String,
) extends ILoop(config, out = new PrintWriter(out)) {
  private var initialized = false

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
      deafultInField.set(this, new RemoteReader(comm, accumulator, completer, initCode))

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

      intp.bind(
        ShellExtensions.BindingName,
        classOf[ShellExtensions].getName,
        new ShellExtensions(new CommunicatorPrintStream(comm)),
      )
      bindings.foreach {
        case (name, ReplBinding(staticType, value)) =>
          intp.bind(name, staticType, value)
      }

      initialized = true
    }
  }
}
