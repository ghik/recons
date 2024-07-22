package com.github.ghik.recons
package core

abstract class Command[T: Encoder : Decoder] {
  def responseEncoder: Encoder[T] = Encoder[T]
  def responseDecoder: Decoder[T] = Decoder[T]
}
