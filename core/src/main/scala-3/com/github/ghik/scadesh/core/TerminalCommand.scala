package com.github.ghik.scadesh
package core

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
