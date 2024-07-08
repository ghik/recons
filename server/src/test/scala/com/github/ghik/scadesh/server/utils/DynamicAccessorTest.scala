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
}

class DynamicAccessorTest extends AnyFunSuite {
  private def dyn(obj: AnyRef): DynamicAccessor = new DynamicAccessor(obj)

  test("simple") {
    val d = dyn(new Something(13))
    assert(d.x == 13)
    assert(d.y == 42)
    assert(d.z == 25)
    assert(d.foo == "foo")
    assert(d.bar == "bar")
  }
}
