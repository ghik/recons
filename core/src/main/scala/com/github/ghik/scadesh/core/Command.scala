package com.github.ghik.scadesh
package core

import org.jline.reader.Parser.ParseContext

import java.io.DataOutput

sealed abstract class Command[T: Encoder : Decoder] {
  def responseEncoder: Encoder[T] = Encoder[T]
  def responseDecoder: Decoder[T] = Decoder[T]
}
sealed abstract class TerminalCommand[T: Encoder : Decoder] extends Command[T]
object TerminalCommand {
  case object ReadLine
    extends TerminalCommand[Option[String]]
  final case class Write(data: Array[Byte])
    extends TerminalCommand[Unit]
  case object Flush
    extends TerminalCommand[Unit]

  implicit def encoder[T]: Encoder[TerminalCommand[T]] = {
    case (out, ReadLine) =>
      out.writeByte(0)
    case (out, Write(data)) =>
      out.writeByte(1)
      Encoder[Array[Byte]].encode(out, data)
    case (out, Flush) =>
      out.writeByte(2)
  }

  implicit def decoder[T]: Decoder[TerminalCommand[T]] =
    in => in.readByte() match {
      case 0 => ReadLine.asInstanceOf[TerminalCommand[T]]
      case 1 => Write(Decoder[Array[Byte]].decode(in)).asInstanceOf[TerminalCommand[T]]
      case 2 => Flush.asInstanceOf[TerminalCommand[T]]
    }
}

sealed abstract class CompilerCommand[T: Encoder : Decoder] extends Command[T]
object CompilerCommand {
  final case class Complete(cursor: Int, line: String)
    extends CompilerCommand[Seq[String]]
  final case class Highlight(line: String)
    extends CompilerCommand[String]
  final case class Parse(input: String, cursor: Int, context: ParseContext)
    extends CompilerCommand[Either[Missing, ParsedLine]]

  implicit def encoder[T]: Encoder[CompilerCommand[T]] = {
    case (out, Complete(cursor, line)) =>
      out.writeByte(0)
      Encoder.encode(out, cursor)
      Encoder.encode(out, line)
    case (out, Highlight(line)) =>
      out.writeByte(1)
      Encoder.encode(out, line)
    case (out, Parse(input, cursor, context)) =>
      out.writeByte(2)
      Encoder.encode(out, input)
      Encoder.encode(out, cursor)
      Encoder.encode(out, context)
  }

  implicit def decoder[T]: Decoder[CompilerCommand[T]] =
    in => in.readByte() match {
      case 0 =>
        val cursor = Decoder[Int].decode(in)
        val line = Decoder[String].decode(in)
        Complete(cursor, line).asInstanceOf[CompilerCommand[T]]
      case 1 =>
        val line = Decoder[String].decode(in)
        Highlight(line).asInstanceOf[CompilerCommand[T]]
      case 2 =>
        val input = Decoder[String].decode(in)
        val cursor = Decoder[Int].decode(in)
        val context = Decoder[ParseContext].decode(in)
        Parse(input, cursor, context).asInstanceOf[CompilerCommand[T]]
    }
}

final case class ParsedLine(cursor: Int, line: String, word: String, wordCursor: Int)
object ParsedLine {
  implicit val encoder: Encoder[ParsedLine] = {
    (out: DataOutput, value: ParsedLine) =>
      Encoder.encode(out, value.cursor)
      Encoder.encode(out, value.line)
      Encoder.encode(out, value.word)
      Encoder.encode(out, value.wordCursor)
  }

  implicit val decoder: Decoder[ParsedLine] = { in =>
    val cursor = Decoder[Int].decode(in)
    val line = Decoder[String].decode(in)
    val word = Decoder[String].decode(in)
    val wordCursor = Decoder[Int].decode(in)
    ParsedLine(cursor, line, word, wordCursor)
  }
}

final case class Missing(prompt: String)
object Missing {
  implicit val encoder: Encoder[Missing] =
    (out, value) => Encoder[String].encode(out, value.prompt)

  implicit val decoder: Decoder[Missing] =
    in => Missing(Decoder[String].decode(in))
}
