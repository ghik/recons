package com.github.ghik.recons
package core

import org.jline.reader.Parser.ParseContext

import java.io.DataOutput

sealed abstract class CompilerCommand[T: Encoder : Decoder] extends Command[T]
object CompilerCommand {
  final case class Complete(cursor: Int, line: String) extends CompilerCommand[Seq[CompletionItem]]
  final case class Highlight(line: String) extends CompilerCommand[String]
  final case class Parse(input: String, cursor: Int, context: ParseContext) extends CompilerCommand[Either[Missing, ParsedLine]]

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

final case class CompletionItem(label: String, signatures: Seq[String])
object CompletionItem {
  implicit val encoder: Encoder[CompletionItem] = {
    (out, value) =>
      Encoder.encode(out, value.label)
      Encoder.encode(out, value.signatures)
  }

  implicit val decoder: Decoder[CompletionItem] = { in =>
    val label = Decoder[String].decode(in)
    val signatures = Decoder[Seq[String]].decode(in)
    CompletionItem(label, signatures)
  }

}

final case class ParsedLine(cursor: Int, line: String, word: String, wordCursor: Int)
object ParsedLine {
  implicit val encoder: Encoder[ParsedLine] = {
    (out, value) =>
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
