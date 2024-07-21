package com.github.ghik.scadesh
package server

import com.github.ghik.scadesh.core.TerminalCommand
import com.github.ghik.scadesh.server.utils.ShellExtensions
import org.jline.reader.*

import scala.tools.nsc.interpreter.shell
import scala.tools.nsc.interpreter.shell.{Accumulator, NoHistory}

/** A Reader that delegates to JLine3.
 */
class RemoteReader(
  comm: ServerCommunicator,
  val accumulator: Accumulator,
  val completion: shell.Completion,
  replConfig: ReplConfig,
) extends shell.InteractiveReader {
  // `scala.tools.nsc.interpreter.jline.Reader` currently uses `HistoryAdaptor`, which does nothing and is marked as TODO.
  // It's unclear why server side needs history at all, this should be a terminal-only, client-side thing.
  override def history: shell.History = NoHistory
  override def interactive: Boolean = true

  private var initLoaded = false

  protected def readOneLine(prompt: String): String =
    if (!initLoaded) {
      initLoaded = true
      val maybeImportExt = if (replConfig.useExtensions) s"import ${ShellExtensions.BindingName}.*" else ""
      List(maybeImportExt, replConfig.initCode).filter(_.nonEmpty).mkString("\n")
    } else {
      comm.sendCommand(TerminalCommand.ReadLine(prompt)).orNull
    }

  protected def getReaderVariable(name: String): String =
    comm.sendCommand(TerminalCommand.GetReaderVariable(name))

  protected def setReaderVariable(name: String, value: String): Unit =
    comm.sendCommand(TerminalCommand.SetReaderVariable(name, value))

  def redrawLine(): Unit = () //see https://github.com/scala/bug/issues/12395, SimpleReader#redrawLine also use `()`
  def reset(): Unit = accumulator.reset()

  override def close(): Unit = {
    comm.sendCommand(TerminalCommand.Close)
    comm.close()
  }

  override def withSecondaryPrompt[T](prompt: String)(body: => T): T = {
    val oldPrompt = getReaderVariable(LineReader.SECONDARY_PROMPT_PATTERN)
    setReaderVariable(LineReader.SECONDARY_PROMPT_PATTERN, prompt)
    try body finally setReaderVariable(LineReader.SECONDARY_PROMPT_PATTERN, oldPrompt)
  }
}
