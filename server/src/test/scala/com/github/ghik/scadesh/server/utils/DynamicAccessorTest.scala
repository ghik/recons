package com.github.ghik.scadesh
package server.utils

import org.scalatest.funsuite.AnyFunSuite

trait Base {
  private val foo = "foo"

  val bar = "bar"
}

class Something(val x: Int) extends Base {
  private val y = 42
  private var z = 25

  def yy = y
  def zz = z
}

class DynamicAccessorTest extends AnyFunSuite {
  private def dyn(obj: AnyRef): DynamicAccessor = new DynamicAccessor(obj)

  test("simple") {
    val d = dyn(new Something(13))
    assert(d.x == (13: Any))
    assert(d.y == (42: Any))
    assert(d.z == (25: Any))
    assert(d.foo == "foo")
    assert(d.bar == "bar")
  }
}
