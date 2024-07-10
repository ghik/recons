package com.github.ghik.scadesh
package server.utils

import scala.language.dynamics

class StaticsDynamicAccessor(private val cls: Class[?]) extends AnyVal with Dynamic {
  def selectDynamic(name: String): AnyRef = {
    val field = cls.getDeclaredField(name)
    field.setAccessible(true)
    field.get(null)
  }
}
