package com.github.ghik.recon
package client

import com.github.ghik.recon.core.*
import org.jline.reader.Parser.ParseContext
import org.jline.reader.impl.LineReaderImpl
import org.jline.reader.impl.history.DefaultHistory
import org.jline.reader.{Candidate, Completer, EOFError, EndOfFileException, Highlighter, LineReader, LineReaderBuilder, Parser, UserInterruptException, ParsedLine as JLineParsedLine}
import org.jline.terminal.TerminalBuilder
import org.jline.utils.AttributedString

import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.List as JList
import scala.jdk.CollectionConverters.*

object TerminalRunner {
  def run(socket: Socket): Unit =
    new TerminalRunner(socket).run()
}
class TerminalRunner(socket: Socket) {
  // import java.util.logging.{Logger, Level}
  // Logger.getLogger("org.jline").setLevel(Level.FINEST)

  private val comm = new ClientCommunicator(socket)

  comm.setCommandHandler(new comm.CommandHandler {
    def handleCommand[T](cmd: TerminalCommand[T]): T = cmd match {
      case TerminalCommand.ReadLine(prompt) =>
        try Option(readLine(prompt)) catch {
          case _: EndOfFileException | _: UserInterruptException => None
        }
      case TerminalCommand.Write(data) =>
        terminal.writer.write(new String(data, StandardCharsets.UTF_8))
      case TerminalCommand.Flush =>
        terminal.writer.flush()
      case TerminalCommand.Close =>
        terminal.close()
    }
  })

  def run(): Unit = comm.receiveLoop()

  private val terminal =
    TerminalBuilder.builder()
      .dumb(System.getenv("TERM") == "dumb") // fail early if not able to create a terminal
      .build()

  private val history = new DefaultHistory

  private val completer: Completer = new Completer {
    private def stripBackTicks(label: String) =
      if label.startsWith("`") && label.endsWith("`") then
        label.drop(1).dropRight(1)
      else
        label

    def complete(reader: LineReader, line: JLineParsedLine, candidates: JList[Candidate]): Unit = {
      def makeCandidate(label: String) = {
        new Candidate(
          /* value    = */ label,
          /* displ    = */ stripBackTicks(label), // displayed value
          /* group    = */ null, // can be used to group completions together
          /* descr    = */ null, // TODO use for documentation?
          /* suffix   = */ null,
          /* key      = */ null,
          /* complete = */ false, // if true adds space when completing
        )
      }
      val comps = comm.sendCommand(CompilerCommand.Complete(line.cursor, line.line))
      candidates.addAll(comps.map(_.label).distinct.map(makeCandidate).asJava)
      val lineWord = line.word()
      comps.filter(c => c.label == lineWord && c.signatures.nonEmpty) match
        case Nil =>
        case exachMatches =>
          val terminal = reader.nn.getTerminal
          reader.callWidget(LineReader.CLEAR)
          terminal.writer.println()
          exachMatches.foreach(_.signatures.foreach(terminal.writer.println))
          reader.callWidget(LineReader.REDRAW_LINE)
          reader.callWidget(LineReader.REDISPLAY)
          terminal.flush()
    }
  }

  /** Provide syntax highlighting */
  private val highlighter: Highlighter = new Highlighter {
    def highlight(reader: LineReader, buffer: String): AttributedString = {
      val highlighted = comm.sendCommand(CompilerCommand.Highlight(buffer))
      AttributedString.fromAnsi(highlighted)
    }
    def setErrorPattern(errorPattern: java.util.regex.Pattern): Unit = ()
    def setErrorIndex(errorIndex: Int): Unit = ()
  }

  private val parser: Parser = new Parser {
    def parse(line: String, cursor: Int, context: ParseContext): JLineParsedLine =
      comm.sendCommand(CompilerCommand.Parse(line, cursor, context)) match {
        case Right(parsedLine) =>
          new JLineParsedLine {
            def word(): String = parsedLine.word
            def wordCursor(): Int = parsedLine.wordCursor
            def line(): String = parsedLine.line
            def cursor(): Int = parsedLine.cursor
            def wordIndex: Int = -1
            def words: JList[String] = java.util.Collections.emptyList[String]
          }
        case Left(Missing(missing)) =>
          throw new EOFError(
            // Using dummy values, not sure what they are used for
            /* line    = */ -1,
            /* column  = */ -1,
            /* message = */ "",
            /* missing = */ missing)
      }
  }

  /** Blockingly read line from `System.in`
   *
   * This entry point into JLine handles everything to do with terminal
   * emulation. This includes:
   *
   *  - Multi-line support
   *  - Copy-pasting
   *  - History
   *  - Syntax highlighting
   *  - Auto-completions
   *
   * @throws EndOfFileException This exception is thrown when the user types Ctrl-D.
   */
  def readLine(prompt: String): String = {
    import LineReader.*
    import LineReader.Option.*

    val userHome = System.getProperty("user.home")
    val lineReader = LineReaderBuilder
      .builder()
      .terminal(terminal)
      .history(history)
      .completer(completer)
      .highlighter(highlighter)
      .parser(parser)
      .variable(HISTORY_FILE, s"$userHome/.dotty_history") // Save history to file
      .variable(SECONDARY_PROMPT_PATTERN, "%M") // A short word explaining what is "missing",
      // this is supplied from the EOFError.getMissing() method
      .variable(LIST_MAX, 400)
      // Ask user when number of completions exceed this limit (default is 100).
      .variable(BLINK_MATCHING_PAREN, 0L)
      // Don't blink the opening paren after typing a closing paren.
      .variable(WORDCHARS,
        LineReaderImpl.DEFAULT_WORDCHARS.filterNot("*?.[]~=/&;!#%^(){}<>".toSet))
      // Finer grained word boundaries
      .option(INSERT_TAB, true)
      // At the beginning of the line, insert tab instead of completing.
      .option(AUTO_FRESH_LINE, true)
      // if not at start of line before prompt, move to new line.
      .option(DISABLE_EVENT_EXPANSION, true)
      // don't process escape sequences in input
      .build()

    lineReader.readLine(prompt)
  }
}
