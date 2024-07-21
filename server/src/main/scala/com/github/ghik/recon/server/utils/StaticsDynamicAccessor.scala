package com.github.ghik.recon
package server.utils

import java.lang.reflect.Modifier
import scala.language.dynamics

class StaticsDynamicAccessor(private val cls: Class[?]) extends AnyVal with Dynamic {
  def selectDynamic(name: String): AnyRef = {
    val field =
      Option(cls.getDeclaredField(name))
        .filter(f => Modifier.isStatic(f.getModifiers))
        .getOrElse(throw new NoSuchFieldException(name))

    field.setAccessible(true)
    field.get(null)
  }

  def applyDynamic(name: String)(args: Any*): AnyRef = {
    val method =
      cls.getDeclaredMethods
        .find(m =>
          Modifier.isStatic(m.getModifiers) &&
            m.getName == name &&
            m.getParameterCount == args.length,
        )
        .getOrElse(throw new NoSuchMethodException(s"$name(${args.length} args)"))

    method.setAccessible(true)
    method.invoke(null, args *)
  }
}
