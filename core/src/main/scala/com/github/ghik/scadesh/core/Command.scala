package com.github.ghik.scadesh
package core

abstract class Command[T: Encoder : Decoder] {
  def responseEncoder: Encoder[T] = Encoder[T]
  def responseDecoder: Decoder[T] = Decoder[T]
}
