package com.github.ghik.recons
package core

sealed abstract class TerminalCommand[T: Encoder : Decoder] extends Command[T]
object TerminalCommand {
  final case class Write(data: Array[Byte]) extends TerminalCommand[Unit]
  case object Flush extends TerminalCommand[Unit]
  case object Close extends TerminalCommand[Unit]
  final case class ReadLine(prompt: String) extends TerminalCommand[Option[String]]
  final case class GetReaderVariable(name: String) extends TerminalCommand[String]
  final case class SetReaderVariable(name: String, value: String) extends TerminalCommand[Unit]

  implicit def encoder[T]: Encoder[TerminalCommand[T]] = {
    (out, value) =>
      value match {
        case Write(data) =>
          out.writeByte(0)
          out.writeInt(data.length)
          out.write(data)
        case Flush =>
          out.writeByte(1)
        case Close =>
          out.writeByte(2)
        case ReadLine(prompt) =>
          out.writeByte(3)
          out.writeUTF(prompt)
        case GetReaderVariable(name) =>
          out.writeByte(4)
          out.writeUTF(name)
        case SetReaderVariable(name, value) =>
          out.writeByte(5)
          out.writeUTF(name)
          out.writeUTF(value)
      }
  }

  implicit def decoder[T]: Decoder[TerminalCommand[T]] =
    in => in.readByte() match {
      case 0 =>
        val data = new Array[Byte](in.readInt())
        in.readFully(data)
        Write(data).asInstanceOf[TerminalCommand[T]]
      case 1 =>
        Flush.asInstanceOf[TerminalCommand[T]]
      case 2 =>
        Close.asInstanceOf[TerminalCommand[T]]
      case 3 =>
        val prompt = in.readUTF()
        ReadLine(prompt).asInstanceOf[TerminalCommand[T]]
      case 4 =>
        val name = in.readUTF()
        GetReaderVariable(name).asInstanceOf[TerminalCommand[T]]
      case 5 =>
        val name = in.readUTF()
        val value = in.readUTF()
        SetReaderVariable(name, value).asInstanceOf[TerminalCommand[T]]
    }
}
