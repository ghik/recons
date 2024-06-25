package com.github.ghik.scadesh
package client

import com.github.ghik.scadesh.core.*
import io.bullet.borer.Codec.ForEither.default
import org.jline.reader.Parser.ParseContext
import org.jline.reader.impl.LineReaderImpl
import org.jline.reader.impl.history.DefaultHistory
import org.jline.reader.{Candidate, Completer, EOFError, EndOfFileException, Highlighter, LineReader, LineReaderBuilder, Parser, UserInterruptException, ParsedLine as JLineParsedLine}
import org.jline.terminal.TerminalBuilder
import org.jline.utils.AttributedString

import java.io.Closeable
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.List as JList

class RemoteJLineTerminal(socket: Socket) extends Closeable {
  // import java.util.logging.{Logger, Level}
  // Logger.getLogger("org.jline").setLevel(Level.FINEST)

  private val comm = new Communicator(socket)

  comm.setCommandHandler(new CommandHandler {
    def handleCommand[T](cmd: Command[T]): T = cmd match {
      case Command.ReadLine =>
        try Option(readLine()) catch {
          case _: EndOfFileException | _: UserInterruptException => None
        }
      case Command.Write(data) =>
        terminal.writer.write(new String(data, StandardCharsets.UTF_8))
        terminal.writer.flush()
      case Command.Flush =>
        terminal.writer.flush()
      case _: CompilerCommand[?] =>
        throw new IllegalArgumentException(s"Unsupported command: $cmd")
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
      val completions = comm.sendCommand(Command.Complete(line.cursor, line.line))
      completions.foreach(label => candidates.add(new Candidate(
        /* value    = */ label,
        /* displ    = */ stripBackTicks(label), // displayed value
        /* group    = */ null, // can be used to group completions together
        /* descr    = */ null, // TODO use for documentation?
        /* suffix   = */ null,
        /* key      = */ null,
        /* complete = */ false, // if true adds space when completing
      )))
    }
  }

  /** Provide syntax highlighting */
  private val highlighter: Highlighter = new Highlighter {
    def highlight(reader: LineReader, buffer: String): AttributedString = {
      val highlighted = comm.sendCommand(Command.Highlight(buffer))
      AttributedString.fromAnsi(highlighted)
    }
    def setErrorPattern(errorPattern: java.util.regex.Pattern): Unit = ()
    def setErrorIndex(errorIndex: Int): Unit = ()
  }

  private val parser: Parser = new Parser {
    def parse(line: String, cursor: Int, context: ParseContext): JLineParsedLine =
      comm.sendCommand(Command.Parse(line, cursor, context)) match {
        case Right(parsedLine) =>
          new JLineParsedLine {
            def word(): String = parsedLine.word
            def wordCursor(): Int = parsedLine.wordCursor
            def line(): String = parsedLine.line
            def cursor(): Int = parsedLine.cursor
            def wordIndex = -1
            def words = java.util.Collections.emptyList[String]
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

  protected def promptStr = "scala"

  private def blue(str: String) = Console.BLUE + str + Console.RESET
  private def prompt = blue(s"\n$promptStr> ")
  private def newLinePrompt = blue("     | ")

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
  def readLine(): String = {
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

  override def close(): Unit = terminal.close()
}
