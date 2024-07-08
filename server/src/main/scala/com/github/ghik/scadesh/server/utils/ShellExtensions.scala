package com.github.ghik.scadesh
package server.utils

import com.github.ghik.scadesh.server.utils.ShellExtensions.UniversalOps

import java.io.PrintStream
import scala.language.implicitConversions

class ShellExtensions(val out: PrintStream) {
  // shadow Predef methods so that the output is sent to the shell client
  def print(x: Any): Unit = out.print(x)
  def println(): Unit = out.println()
  def println(x: Any): Unit = out.println(x)
  def printf(format: String, args: Any*): Unit = out.printf(format, args *)

  implicit def universalOps[T](value: T): UniversalOps[T] =
    new ShellExtensions.UniversalOps(value)
}

object ShellExtensions {
  final val BindingName = "$ext"

  class UniversalOps[T](private val value: T) extends AnyVal {
    def d: DynamicAccessor = new DynamicAccessor(value.asInstanceOf[AnyRef])

    def as[U]: U = value.asInstanceOf[U]
  }
}
