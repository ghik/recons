package com.github.ghik.recon
package client

import com.github.ghik.recon.core.{CompilerCommand, ParseResult, TerminalCommand}
import org.jline.builtins.InputRC
import org.jline.reader._
import org.jline.reader.impl.history.DefaultHistory
import org.jline.reader.impl.{CompletionMatcherImpl, DefaultParser, LineReaderImpl}
import org.jline.terminal.TerminalBuilder

import java.io.{ByteArrayInputStream, File}
import java.net.{MalformedURLException, Socket, URI, URL}
import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.tools.nsc.GenericRunnerSettings
import scala.tools.nsc.interpreter.jline.Completion
import scala.tools.nsc.interpreter.shell.{CompletionResult, ShellConfig}
import scala.tools.nsc.interpreter.{Naming, shell}
import scala.util.Using
import scala.util.control.NonFatal

object TerminalRunner {
  def run(socket: Socket): Unit = {
    new TerminalRunner(socket).run()
  }
}
class TerminalRunner(socket: Socket) {
  def run(): Unit = {
    comm.receiveLoop()
  }

  private val terminal = TerminalBuilder.builder().jna(true).build()
  private val comm = new ClientCommunicator(socket)

  comm.setCommandHandler(new comm.CommandHandler {
    def handleCommand[T](cmd: TerminalCommand[T]): T = cmd match {
      case TerminalCommand.Write(data) =>
        terminal.writer().write(new String(data, StandardCharsets.UTF_8))
      case TerminalCommand.ReadLine(prompt) =>
        try Option(lineReader.readLine(prompt)) catch {
          case _: EndOfFileException | _: UserInterruptException =>
            lineReader.getBuffer.delete()
            None
        }
      case TerminalCommand.Flush =>
        terminal.writer.flush()
      case TerminalCommand.Close =>
        terminal.close()
      case TerminalCommand.GetReaderVariable(name) =>
        lineReader.getVariable(name).asInstanceOf[String]
      case TerminalCommand.SetReaderVariable(name, value) =>
        lineReader.setVariable(name, value)
    }
  })

  private val parser = new Parser {
    private val defaultParser = new DefaultParser

    def parse(line: String, cursor: Int, context: Parser.ParseContext): ParsedLine = {
      if (line.startsWith(":")) defaultParser.parse(line, cursor, context)
      else comm.sendCommand(CompilerCommand.Parse(line, cursor, context)) match {
        case ParseResult.Success(parsedLine) => parsedLine
        case ParseResult.SyntaxError => throw new SyntaxError(0, 0, "incomplete")
        case ParseResult.EOFError => throw new EOFError(0, 0, "incomplete")
      }
    }
  }

  private val completer = new shell.Completion {
    def complete(buffer: String, cursor: Int, filter: Boolean): CompletionResult =
      comm.sendCommand(CompilerCommand.Complete(buffer, cursor, filter)).result
  }

  // Implementation mostly copied from `scala.tools.nsc.interpreter.jline.Reader.apply`
  private val lineReader = {
    // This config is empty TODO: hardcode or make it possible to pass settings from outside
    val config: ShellConfig =
      ShellConfig(new GenericRunnerSettings(s => if (s.nonEmpty) terminal.writer.println(s)))

    System.setProperty(LineReader.PROP_SUPPORT_PARSEDLINE, java.lang.Boolean.TRUE.toString)

    def inputrcFileUrl(): Option[URL] = {
      sys.props
        .get("jline.inputrc")
        .flatMap { path =>
          try Some(new URI(path).toURL)
          catch {
            case _: MalformedURLException =>
              Some(new File(path).toURI.toURL)
          }
        }.orElse {
          sys.props.get("user.home").map { home =>
            val f = new File(home).toPath.resolve(".inputrc").toFile
            (if (f.isFile) f else new File("/etc/inputrc")).toURI.toURL
          }
        }
    }

    def urlByteArray(url: URL): Array[Byte] = {
      Using.resource(Source.fromURL(url).bufferedReader()) {
        bufferedReader =>
          LazyList.continually(bufferedReader.read).takeWhile(_ != -1).map(_.toByte).toArray
      }
    }

    lazy val inputrcFileContents: Option[Array[Byte]] = inputrcFileUrl().map(in => urlByteArray(in))

    val history = new DefaultHistory

    val builder =
      LineReaderBuilder.builder()
        .appName("scala")
        .completer(new Completion(completer))
        .history(history)
        .parser(parser)
        .terminal(terminal)

    locally {
      import LineReader._
      import Option._
      builder
        .option(AUTO_GROUP, false)
        .option(LIST_PACKED, true) // TODO
        .option(INSERT_TAB, true) // At the beginning of the line, insert tab instead of completing
        .variable(HISTORY_FILE, config.historyFile) // Save history to file
        .variable(SECONDARY_PROMPT_PATTERN, config.encolor(config.continueText)) // Continue prompt
        .variable(WORDCHARS, LineReaderImpl.DEFAULT_WORDCHARS.filterNot("*?.[]~=/&;!#%^(){}<>".toSet))
        .option(Option.DISABLE_EVENT_EXPANSION, true) // Otherwise `scala> println(raw"\n".toList)` gives `List(n)` !!
        .option(Option.COMPLETE_MATCHER_CAMELCASE, true)
        .option(Option.COMPLETE_MATCHER_TYPO, true)
    }
    object customCompletionMatcher extends CompletionMatcherImpl {
      override def compile(
        options: java.util.Map[LineReader.Option, java.lang.Boolean],
        prefix: Boolean,
        line: CompletingParsedLine,
        caseInsensitive: Boolean,
        errors: Int,
        originalGroupName: String,
      ): Unit = {
        val errorsReduced = line.wordCursor() match {
          case 0 | 1 | 2 | 3 => 0 // disable JLine's levenshtein-distance based typo matcher for short strings
          case 4 | 5 => math.max(errors, 1)
          case _ => errors
        }
        super.compile(options, prefix, line, caseInsensitive, errorsReduced, originalGroupName)
      }
    }

    builder.completionMatcher(customCompletionMatcher)

    val reader = builder.build()
    try inputrcFileContents.foreach(f => InputRC.configure(reader, new ByteArrayInputStream(f))) catch {
      case NonFatal(_) =>
    } //ignore

    object ScalaShowType {
      val Name = "scala-show-type"
      private var lastInvokeLocation: Option[(String, Int)] = None
      def apply(): Boolean = {
        val nextInvokeLocation = Some((reader.getBuffer.toString, reader.getBuffer.cursor()))
        val cursor = reader.getBuffer.cursor()
        val text = reader.getBuffer.toString
        val result = completer.complete(text, cursor, filter = true)
        if (lastInvokeLocation == nextInvokeLocation) {
          show(Naming.unmangle(result.typedTree))
          lastInvokeLocation = None
        } else {
          show(result.typeAtCursor)
          lastInvokeLocation = nextInvokeLocation
        }
        true
      }
      def show(text: String): Unit = if (text != "") {
        reader.callWidget(LineReader.CLEAR)
        reader.getTerminal.writer.println()
        reader.getTerminal.writer.println(text)
        reader.callWidget(LineReader.REDRAW_LINE)
        reader.callWidget(LineReader.REDISPLAY)
        reader.getTerminal.flush()
      }
    }
    reader.getWidgets.put(ScalaShowType.Name, () => ScalaShowType())

    def secure(p: java.nio.file.Path): Unit = {
      try scala.reflect.internal.util.OwnerOnlyChmod.chmodFileOrCreateEmpty(p)
      catch {
        case scala.util.control.NonFatal(e) =>
          if (config.isReplDebug) e.printStackTrace()
          config.replinfo(s"Warning: history file ${p}'s permissions could not be restricted to owner-only.")
      }
    }
    def backupHistory(): Unit = {
      import java.nio.file.{Files, Paths, StandardCopyOption}
      import StandardCopyOption.REPLACE_EXISTING
      val hf = Paths.get(config.historyFile)
      val bk = Paths.get(config.historyFile + ".bk")
      Files.move(/*source =*/ hf, /*target =*/ bk, REPLACE_EXISTING)
      secure(bk)
    }
    // always try to restrict permissions on history file,
    // creating an empty file if none exists.
    secure(java.nio.file.Paths.get(config.historyFile))
    try history.attach(reader) catch {
      case e: IllegalArgumentException if e.getMessage.contains("Bad history file syntax") =>
        backupHistory()
        history.attach(reader)
      case _: NumberFormatException =>
        backupHistory()
        history.attach(reader)
    }

    reader
  }
}
