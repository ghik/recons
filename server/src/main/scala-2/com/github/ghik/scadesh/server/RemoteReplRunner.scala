package com.github.ghik.scadesh
package server

import java.io.PrintWriter
import java.net.Socket
import scala.tools.nsc.interpreter.shell.{Accumulator, ILoop, InteractiveReader, ShellConfig}
import scala.tools.nsc.{GenericRunnerSettings, Settings}
import scala.util.control.NonFatal

object RemoteReplRunner {
  def run(args: Array[String], socket: Socket): Unit = {
    val comm = new ServerCommunicator(socket)
    val out = new PrintWriter(new CommunicatorOutputStream(comm))
    val settings = new GenericRunnerSettings(s => if (s.nonEmpty) out.println(s))
    settings.processArguments(args.toList, processAll = false)
    val config = ShellConfig(settings)
    new RemoteReplRunner(comm, config, out).run(settings)
  }
}
class RemoteReplRunner(comm: ServerCommunicator, config: ShellConfig, out: PrintWriter) extends ILoop(config, out = out) {
  private var readerReplaced = false

  override def createInterpreter(interpreterSettings: Settings): Unit = {
    super.createInterpreter(interpreterSettings)

    if (!readerReplaced) {
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
      deafultInField.set(this, new RemoteReader(comm, accumulator, completion(accumulator)))

      readerReplaced = true
    }
  }
}
