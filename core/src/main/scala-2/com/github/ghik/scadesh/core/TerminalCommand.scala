package com.github.ghik.scadesh
package core

sealed abstract class TerminalCommand[T: Encoder : Decoder] extends Command[T]
object TerminalCommand {
  case class Write(data: Array[Byte]) extends TerminalCommand[Unit]
  case object Flush extends TerminalCommand[Unit]

  implicit def encoder[T]: Encoder[TerminalCommand[T]] = ???
  implicit def decoder[T]: Decoder[TerminalCommand[T]] = ???
}
