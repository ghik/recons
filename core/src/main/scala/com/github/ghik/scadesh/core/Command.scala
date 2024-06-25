package com.github.ghik.scadesh
package core

import io.bullet.borer.derivation.ArrayBasedCodecs.*
import io.bullet.borer.{Codec, Decoder, Encoder}
import org.jline.reader.Parser.ParseContext

sealed abstract class Command[T: Encoder] {
  def responseEncoder: Encoder[T] = Encoder[T]
}
sealed abstract class TerminalCommand[T: Encoder] extends Command[T]
sealed abstract class CompilerCommand[T: Encoder] extends Command[T]
object Command {

  import Codec.ForEither.default

  given Codec[ParseContext] =
    Codec.of[Int].bimap(_.ordinal, ParseContext.values.apply)

  given Codec[Command[?]] = deriveCodec

  case object ReadLine
    extends TerminalCommand[Option[String]] derives Codec
  final case class Write(data: Array[Byte])
    extends TerminalCommand[Unit] derives Codec
  case object Flush
    extends TerminalCommand[Unit] derives Codec

  final case class Complete(cursor: Int, line: String)
    extends CompilerCommand[Seq[String]] derives Codec
  final case class Highlight(line: String)
    extends CompilerCommand[String] derives Codec
  final case class Parse(input: String, cursor: Int, context: ParseContext)
    extends CompilerCommand[Either[Missing, ParsedLine]] derives Codec
}

final case class ParsedLine(cursor: Int, line: String, word: String, wordCursor: Int)
  derives Codec

final case class Missing(prompt: String)
  derives Codec
