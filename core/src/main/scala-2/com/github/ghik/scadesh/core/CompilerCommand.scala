package com.github.ghik.scadesh
package core

sealed abstract class CompilerCommand[T: Encoder : Decoder] extends Command[T]
object CompilerCommand {
  implicit def encoder[T]: Encoder[CompilerCommand[T]] = ???
  implicit def decoder[T]: Decoder[CompilerCommand[T]] = ???
}
