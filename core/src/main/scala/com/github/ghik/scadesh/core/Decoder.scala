package com.github.ghik.scadesh
package core

import java.io.DataInput
import scala.reflect.{ClassTag, classTag}

trait Decoder[T] {
  def decode(in: DataInput): T
}
object Decoder {
  def apply[T](implicit dec: Decoder[T]): Decoder[T] = dec

  def decode[T: Decoder](in: DataInput): T =
    Decoder[T].decode(in)

  implicit val NothingDecoder: Decoder[Nothing] = _ => sys.error("Nothing")
  implicit val UnitDecoder: Decoder[Unit] = _ => ()
  implicit val IntDecoder: Decoder[Int] = _.readInt()
  implicit val BooleanDecoder: Decoder[Boolean] = _.readBoolean()
  implicit val StringDecoder: Decoder[String] = _.readUTF()

  implicit val BytesDecoder: Decoder[Array[Byte]] = { in =>
    val len = in.readInt()
    val bytes = new Array[Byte](len)
    in.readFully(bytes)
    bytes
  }

  implicit def jEnumDecoder[E <: Enum[E] : ClassTag]: Decoder[E] =
    in => classTag[E].runtimeClass.asInstanceOf[Class[E]].getEnumConstants.apply(in.readInt())

  implicit def optionDecoder[T: Decoder]: Decoder[Option[T]] =
    in => if (in.readBoolean()) Some(Decoder[T].decode(in)) else None

  implicit def seqDecoder[T: Decoder]: Decoder[Seq[T]] =
    in => Vector.fill(in.readInt())(Decoder[T].decode(in))

  implicit def eitherDecoder[L: Decoder, R: Decoder]: Decoder[Either[L, R]] = in =>
    if (in.readBoolean()) Right(Decoder[R].decode(in))
    else Left(Decoder[L].decode(in))
}
