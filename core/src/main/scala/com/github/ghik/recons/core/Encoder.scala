package com.github.ghik.recons
package core

import java.io.DataOutput

trait Encoder[T] {
  def encode(out: DataOutput, value: T): Unit
}
object Encoder {
  def apply[T](implicit enc: Encoder[T]): Encoder[T] = enc

  def encode[T: Encoder](out: DataOutput, value: T): Unit =
    Encoder[T].encode(out, value)

  implicit val NothingEncoder: Encoder[Nothing] = (_, _) => ()
  implicit val UnitEncoder: Encoder[Unit] = (_, _) => ()
  implicit val IntEncoder: Encoder[Int] = _.writeInt(_)
  implicit val BooleanEncoder: Encoder[Boolean] = _.writeBoolean(_)
  implicit val StringEncoder: Encoder[String] = _.writeUTF(_)

  implicit val BytesEncoder: Encoder[Array[Byte]] = (out, bytes) => {
    out.writeInt(bytes.length)
    out.write(bytes)
  }

  implicit def jEnumEncoder[E <: Enum[E]]: Encoder[E] =
    (out, value) => out.writeInt(value.ordinal)

  implicit def optionEncoder[T: Encoder]: Encoder[Option[T]] = {
    case (out, Some(value)) =>
      out.writeBoolean(true)
      Encoder[T].encode(out, value)
    case (out, None) =>
      out.writeBoolean(false)
  }

  implicit def seqEncoder[T: Encoder]: Encoder[Seq[T]] = (out, seq) => {
    out.writeInt(seq.size)
    seq.foreach(Encoder[T].encode(out, _))
  }

  implicit def eitherEncoder[L: Encoder, R: Encoder]: Encoder[Either[L, R]] = {
    case (out, Left(value)) =>
      out.writeBoolean(false)
      Encoder[L].encode(out, value)
    case (out, Right(value)) =>
      out.writeBoolean(true)
      Encoder[R].encode(out, value)
  }
}
