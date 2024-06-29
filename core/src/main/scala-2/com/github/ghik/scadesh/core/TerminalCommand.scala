package com.github.ghik.scadesh
package core

sealed abstract class TerminalCommand[T: Encoder : Decoder] extends Command[T]
object TerminalCommand {
  final case class Write(data: Array[Byte]) extends TerminalCommand[Unit]
  case object Flush extends TerminalCommand[Unit]
  case object Close extends TerminalCommand[Unit]
  final case class ReadLine(prompt: String) extends TerminalCommand[String]
  final case class GetReaderVariable(name: String) extends TerminalCommand[String]
  final case class SetReaderVariable(name: String, value: String) extends TerminalCommand[Unit]

  implicit def encoder[T]: Encoder[TerminalCommand[T]] = ???
  implicit def decoder[T]: Decoder[TerminalCommand[T]] = ???
}
