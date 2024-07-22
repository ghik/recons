package com.github.ghik.recons
package core

sealed abstract class TerminalCommand[T: Encoder : Decoder] extends Command[T]
object TerminalCommand {
  case class ReadLine(prompt: String) extends TerminalCommand[Option[String]]
  final case class Write(data: Array[Byte]) extends TerminalCommand[Unit]
  case object Flush extends TerminalCommand[Unit]
  case object Close extends TerminalCommand[Unit]

  implicit def encoder[T]: Encoder[TerminalCommand[T]] = {
    case (out, ReadLine(prompt)) =>
      out.writeByte(0)
      Encoder[String].encode(out, prompt)
    case (out, Write(data)) =>
      out.writeByte(1)
      Encoder[Array[Byte]].encode(out, data)
    case (out, Flush) =>
      out.writeByte(2)
    case (out, Close) =>
      out.writeByte(3)
  }

  implicit def decoder[T]: Decoder[TerminalCommand[T]] =
    in => in.readByte() match {
      case 0 => ReadLine(Decoder[String].decode(in)).asInstanceOf[TerminalCommand[T]]
      case 1 => Write(Decoder[Array[Byte]].decode(in)).asInstanceOf[TerminalCommand[T]]
      case 2 => Flush.asInstanceOf[TerminalCommand[T]]
      case 3 => Close.asInstanceOf[TerminalCommand[T]]
    }
}
