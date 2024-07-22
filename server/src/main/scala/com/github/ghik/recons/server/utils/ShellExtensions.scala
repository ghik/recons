package com.github.ghik.recons
package server.utils

import com.github.ghik.recons.server.utils.ShellExtensions.{ClassOps, UniversalOps}

import java.io.PrintStream
import scala.language.implicitConversions
import scala.reflect.{ClassTag, classTag}

class ShellExtensions(val out: PrintStream) {
  // shadow Predef methods so that the output is sent to the shell client
  def print(x: Any): Unit = out.print(x)
  def println(): Unit = out.println()
  def println(x: Any): Unit = out.println(x)
  def printf(format: String, args: Any*): Unit = out.printf(format, args *)

  def statics[C: ClassTag]: StaticsDynamicAccessor =
    new StaticsDynamicAccessor(classTag[C].runtimeClass)

  implicit def universalOps[T](value: T): UniversalOps[T] =
    new ShellExtensions.UniversalOps(value)

  implicit def classOps[T](cls: Class[T]): ClassOps[T] =
    new ClassOps(cls)
}

object ShellExtensions {
  final val BindingName = "$ext"

  class UniversalOps[T](private val value: T) extends AnyVal {
    def d: DynamicAccessor = new DynamicAccessor(value.asInstanceOf[AnyRef])

    def as[U]: U = value.asInstanceOf[U]
  }

  class ClassOps[T](private val cls: Class[T]) extends AnyVal {
    def d: StaticsDynamicAccessor = new StaticsDynamicAccessor(cls)
  }
}
