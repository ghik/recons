package com.github.ghik.scadesh
package server

import com.github.ghik.scadesh.core.{Command, ParsedLine, *}
import dotty.tools.dotc.config.Properties.{javaVersion, javaVmName, simpleVersionString}
import dotty.tools.dotc.core.Contexts.*
import dotty.tools.dotc.parsing.Scanners.Scanner
import dotty.tools.dotc.parsing.Tokens.*
import dotty.tools.dotc.printing.SyntaxHighlighting
import dotty.tools.dotc.reporting.Reporter
import dotty.tools.dotc.util.SourceFile
import dotty.tools.repl.*
import org.jline.reader
import org.jline.reader.*
import org.jline.reader.Parser.ParseContext

import java.io.PrintStream
import java.net.Socket
import scala.annotation.tailrec

class RemoteReplDriver private(
  settings: Array[String],
  comm: Communicator,
  out: PrintStream,
  classLoader: Option[ClassLoader],
) extends ReplDriver(settings, out, classLoader) { self =>
  private def this(settings: Array[String], comm: Communicator, classLoader: Option[ClassLoader]) =
    this(settings, comm, new CommunicatorPrintStream(comm), classLoader)

  def this(settings: Array[String], socket: Socket, classLoader: Option[ClassLoader] = None) =
    this(settings, new Communicator(socket), classLoader)

  // implementation copied from superclass, only with JLineTerminal replaced by RemoteTerminal
  override def runUntilQuit(using initialState: State)(): State = {
    out.println(
      s"""Welcome to Debug Shell, based on Scala $simpleVersionString ($javaVersion, Java $javaVmName).
         |Type in expressions for evaluation. Or try :help.""".stripMargin)

    /** Blockingly read a line, getting back a parse result */
    def readLine()(using state: State): ParseResult = {
      given Context = state.context

      comm.setCommandHandler(new CommandHandler {
        def handleCommand[T](cmd: Command[T]): T = cmd match {
          case Command.Complete(cursor, line) =>
            completions(cursor, line, state).map(_.value)
          case Command.Highlight(line) =>
            SyntaxHighlighting.highlight(line)
          case Command.Parse(input, cursor, context) =>
            parse(input, cursor, context)
          case _: TerminalCommand[?] =>
            throw new IllegalArgumentException(s"Unsupported command: $cmd")
        }
      })

      comm.sendCommand(Command.ReadLine)
        .map(ParseResult(_))
        .getOrElse(Quit)
    }

    @tailrec def loop(using state: State)(): State = {
      val res = readLine()
      if (res == Quit) state
      else loop(using interpret(res))()
    }

    try runBody {
      loop()
    }
    finally comm.close()
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
      case ParseContext.COMPLETE if RemoteReplDriver.commands.exists(_.startsWith(input)) =>
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
object RemoteReplDriver {
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
