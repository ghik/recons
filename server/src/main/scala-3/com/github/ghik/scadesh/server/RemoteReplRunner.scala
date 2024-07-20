package com.github.ghik.scadesh
package server

import com.github.ghik.scadesh.core.{ParsedLine, *}
import com.github.ghik.scadesh.server.utils.ShellExtensions
import dotty.tools.dotc.core.Contexts.*
import dotty.tools.dotc.parsing.Scanners.Scanner
import dotty.tools.dotc.parsing.Tokens.*
import dotty.tools.dotc.printing.SyntaxHighlighting
import dotty.tools.dotc.reporting.Reporter
import dotty.tools.dotc.util.SourceFile
import dotty.tools.repl.*
import dotty.tools.repl.Rendering.showUser
import org.jline.reader
import org.jline.reader.*
import org.jline.reader.Parser.ParseContext

import java.io.PrintStream
import java.net.Socket
import scala.annotation.tailrec

class RemoteReplRunner private(
  settings: Array[String],
  comm: ServerCommunicator,
  out: PrintStream,
  config: ReplConfig,
) extends ReplDriver(settings, out, Some(classOf[RemoteReplRunner].getClassLoader)) { self =>
  override protected def redirectOutput: Boolean = false

  // implementation copied from superclass, only with JLineTerminal replaced by RemoteTerminal
  override def runUntilQuit(using initialState: State)(): State = {
    out.println(config.welcome)

    /** Blockingly read a line, getting back a parse result */
    def readLine()(using state: State): ParseResult = {
      given Context = state.context

      comm.setCommandHandler(new comm.CommandHandler {
        def handleCommand[T](cmd: CompilerCommand[T]): T = cmd match {
          case CompilerCommand.Complete(cursor, line) =>
            completionsWithSignatures(cursor, line, state).map { comp =>
              val signatures = comp.symbols.map(s => SyntaxHighlighting.highlight(s.showUser))
              CompletionItem(comp.label, signatures)
            }
          case CompilerCommand.Highlight(line) =>
            SyntaxHighlighting.highlight(line)
          case CompilerCommand.Parse(input, cursor, context) =>
            parse(input, cursor, context)
        }
      })

      comm.sendCommand(TerminalCommand.ReadLine("\n" + blue(config.prompt)))
        .map(ParseResult(_))
        .getOrElse(Quit)
    }

    @tailrec def loop(using state: State)(): State = {
      val res = readLine()
      if (res == Quit) state
      else loop(using interpret(res))()
    }

    val fullBindings = Map("$ext" -> ReplBinding.forClass(new ShellExtensions(out))) ++ config.bindings
    val bindingDecls = ReplBindingHelpers.declarations(fullBindings)
    val fullInitCode = List(bindingDecls, "import $ext.*", config.initCode)
      .filter(_.nonEmpty).mkString("\n")

    def interpretInit(using state: State)(): State =
      if (fullInitCode.isBlank) state
      else ReplBindingHelpers.withBindings(fullBindings)(interpret(ParseResult(fullInitCode)))

    try runBody {
      loop(using interpretInit())()
    } finally {
      comm.sendCommand(TerminalCommand.Close)
      comm.close()
    }
  }

  private def blue(str: String)(using Context) =
    if (ctx.settings.color.value != "never") Console.BLUE + str + Console.RESET
    else str

  private def newLinePrompt(using Context) = blue("     | ")

  private def parse(input: String, cursor: Int, parseContext: ParseContext)(using Context): Either[Missing, ParsedLine] = {
    def parsedLine(word: String, wordCursor: Int) =
      Right(ParsedLine(cursor, input, word, wordCursor))

    // Used when no word is being completed
    def defaultParsedLine = parsedLine("", 0)

    def incomplete(): Either[Missing, ParsedLine] =
      Left(Missing(newLinePrompt))

    case class TokenData(token: Token, start: Int, end: Int)
    def currentToken: TokenData /* | Null */ = {
      val source = SourceFile.virtual("<completions>", input)
      val scanner = new Scanner(source)(using ctx.fresh.setReporter(Reporter.NoReporter))
      var lastBacktickErrorStart: Option[Int] = None

      while (scanner.token != EOF) {
        val start = scanner.offset
        val token = scanner.token
        scanner.nextToken()
        val end = scanner.lastOffset

        val isCurrentToken = cursor >= start && cursor <= end
        if (isCurrentToken)
          return TokenData(token, lastBacktickErrorStart.getOrElse(start), end)


        // we need to enclose the last backtick, which unclosed produces ERROR token
        if token == ERROR && input(start) == '`' then
          lastBacktickErrorStart = Some(start)
        else
          lastBacktickErrorStart = None
      }
      null
    }

    def acceptLine = {
      val onLastLine = !input.substring(cursor).contains(System.lineSeparator)
      onLastLine && !ParseResult.isIncomplete(input)
    }

    parseContext match {
      case ParseContext.ACCEPT_LINE if acceptLine =>
        // using dummy values, resulting parsed input is probably unused
        defaultParsedLine

      // In the situation where we have a partial command that we want to
      // complete we need to ensure that the :<partial-word> isn't split into
      // 2 tokens, but rather the entire thing is treated as the "word", in
      //   order to insure the : is replaced in the completion.
      case ParseContext.COMPLETE if RemoteReplRunner.commands.exists(_.startsWith(input)) =>
        parsedLine(input, cursor)

      case ParseContext.COMPLETE =>
        // Parse to find completions (typically after a Tab).
        def isCompletable(token: Token) = isIdentifier(token) || isKeyword(token)
        currentToken match {
          case TokenData(token, start, end) if isCompletable(token) =>
            val word = input.substring(start, end)
            val wordCursor = cursor - start
            parsedLine(word, wordCursor)
          case _ =>
            defaultParsedLine
        }

      case _ =>
        incomplete()
    }
  }
}
object RemoteReplRunner {
  def run(
    settings: Array[String],
    socket: Socket,
    config: ReplConfig,
  ): Unit = {
    val comm = new ServerCommunicator(socket)
    val out = new CommunicatorPrintStream(comm)
    new RemoteReplRunner(settings, comm, out, config).tryRunning
  }

  private val commands: List[String] = List(
    Quit.command,
    Quit.alias,
    Help.command,
    Reset.command,
    Imports.command,
    Load.command,
    TypeOf.command,
    DocOf.command,
    Settings.command,
  )
}
