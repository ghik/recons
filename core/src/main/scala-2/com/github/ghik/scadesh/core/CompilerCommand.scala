package com.github.ghik.recons
package core

import org.jline.reader.Parser.ParseContext

import scala.tools.nsc.interpreter.jline.Reader.ScalaParsedLine
import scala.tools.nsc.interpreter.shell.CompletionResult
import scala.tools.nsc.interpreter.{CompletionCandidate, TokenData}

sealed abstract class CompilerCommand[T: Encoder : Decoder] extends Command[T]
object CompilerCommand {
  final case class Parse(line: String, cursor: Int, context: ParseContext) extends CompilerCommand[ParseResult]
  final case class Complete(buffer: String, cursor: Int, filter: Boolean) extends CompilerCommand[CompleteResult]

  implicit def encoder[T]: Encoder[CompilerCommand[T]] = {
    case (out, Parse(line, cursor, context)) =>
      out.writeByte(0)
      out.writeUTF(line)
      out.writeInt(cursor)
      Encoder.encode(out, context)
    case (out, Complete(buffer, cursor, filter)) =>
      out.writeByte(1)
      out.writeUTF(buffer)
      out.writeInt(cursor)
      out.writeBoolean(filter)
  }

  implicit def decoder[T]: Decoder[CompilerCommand[T]] =
    in => in.readByte() match {
      case 0 =>
        val line = in.readUTF()
        val cursor = in.readInt()
        val context = Decoder.decode[ParseContext](in)
        Parse(line, cursor, context).asInstanceOf[CompilerCommand[T]]
      case 1 =>
        val buffer = in.readUTF()
        val cursor = in.readInt()
        val filter = in.readBoolean()
        Complete(buffer, cursor, filter).asInstanceOf[CompilerCommand[T]]
    }
}

sealed abstract class ParseResult
object ParseResult {
  final case class Success(parsedLine: ScalaParsedLine) extends ParseResult
  case object SyntaxError extends ParseResult
  case object EOFError extends ParseResult

  implicit def encoder: Encoder[ParseResult] =
    (out, value) => value match {
      case Success(parsedLine) =>
        out.writeByte(0)
        out.writeUTF(parsedLine.line)
        out.writeInt(parsedLine.cursor)
        out.writeInt(parsedLine.wordCursor)
        out.writeInt(parsedLine.wordIndex)
        out.writeInt(parsedLine.tokens.size)
        parsedLine.tokens.foreach { token =>
          out.writeInt(token.token)
          out.writeInt(token.start)
          out.writeInt(token.end)
          out.writeBoolean(token.isIdentifier)
        }
      case SyntaxError =>
        out.writeByte(1)
      case EOFError =>
        out.writeByte(2)
    }

  implicit def decoder: Decoder[ParseResult] =
    in => in.readByte() match {
      case 0 =>
        val line = in.readUTF()
        val cursor = in.readInt()
        val wordCursor = in.readInt()
        val wordIndex = in.readInt()
        val tokensSize = in.readInt()
        val tokens = List.fill(tokensSize) {
          val token = in.readInt()
          val start = in.readInt()
          val end = in.readInt()
          val isIdentifier = in.readBoolean()
          TokenData(token, start, end, isIdentifier)
        }
        Success(ScalaParsedLine(line, cursor, wordCursor, wordIndex, tokens))
      case 1 =>
        SyntaxError
      case 2 =>
        EOFError
    }
}

final case class CompleteResult(result: CompletionResult)
object CompleteResult {
  implicit def encoder: Encoder[CompleteResult] = { (out, value) =>
    out.writeUTF(value.result.line)
    out.writeInt(value.result.cursor)
    out.writeInt(value.result.candidates.size)
    value.result.candidates.foreach { candidate =>
      out.writeUTF(candidate.name)
      out.writeByte(candidate.arity match {
        case CompletionCandidate.Nullary => 0
        case CompletionCandidate.Nilary => 1
        case CompletionCandidate.Infix => 2
        case CompletionCandidate.Other => 3
      })
      out.writeBoolean(candidate.isDeprecated)
      out.writeBoolean(candidate.isUniversal)
      out.writeUTF(candidate.declString())
      Encoder.encode(out, candidate.alias)
    }
    out.writeUTF(value.result.typeAtCursor)
    out.writeUTF(value.result.typedTree)
  }

  implicit def decoder: Decoder[CompleteResult] = { in =>
    val line = in.readUTF()
    val cursor = in.readInt()
    val candidatesSize = in.readInt()
    val candidates = List.fill(candidatesSize) {
      val name = in.readUTF()
      val arity = in.readByte() match {
        case 0 => CompletionCandidate.Nullary
        case 1 => CompletionCandidate.Nilary
        case 2 => CompletionCandidate.Infix
        case 3 => CompletionCandidate.Other
      }
      val isDeprecated = in.readBoolean()
      val isUniversal = in.readBoolean()
      val declString = in.readUTF()
      val alias = Decoder.decode[Option[String]](in)
      CompletionCandidate(name, arity, isDeprecated, isUniversal, () => declString, alias)
    }
    val typeAtCursor = in.readUTF()
    val typedTree = in.readUTF()
    CompleteResult(CompletionResult(line, cursor, candidates, typeAtCursor, typedTree))
  }
}
