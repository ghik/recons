package com.github.ghik.scadesh
package server.utils

import java.lang.reflect.{Field, Modifier}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.language.dynamics
import scala.util.{Failure, Success, Try}

class DynamicAccessor(obj: AnyRef) extends Dynamic {

  private def allInterfaces(cls: Class[_]): List[Class[_]] = {
    val expanded = new mutable.HashSet[Class[_]]

    def expand(iface: Class[_]): Iterator[Class[_]] =
      if (expanded.add(iface))
        Iterator(iface) ++ iface.getInterfaces.iterator.flatMap(expand)
      else
        Iterator.empty

    Iterator.iterate[Class[_]](cls)(_.getSuperclass)
      .takeWhile(_ != null)
      .flatMap(_.getInterfaces.iterator)
      .flatMap(expand)
      .toList
  }

  private def possibleFieldNames(cls: Class[_], name: String): List[String] =
    name :: allInterfaces(cls).map { iface =>
      iface.getName.replace('.', '$') + "$$" + name
    }

  @tailrec
  private def findClassField(cls: Class[_], names: List[String]): Field =
    if (cls == null) null else {
      @tailrec def loop(names: List[String]): Field = names match {
        case Nil => null
        case head :: tail =>
          Try(cls.getDeclaredField(head)) match {
            case Success(field) if !Modifier.isStatic(field.getModifiers) => field
            case Success(_) | Failure(_: NoSuchFieldException) => loop(tail)
            case Failure(f) => throw f
          }
      }

      loop(names) match {
        case null => findClassField(cls.getSuperclass, names)
        case field => field
      }
    }

  private def findField(cls: Class[_], name: String): Field =
    findClassField(cls, possibleFieldNames(cls, name)) match {
      case null => throw new NoSuchFieldException(name)
      case f =>
        f.setAccessible(true)
        f
    }

  def selectDynamic(name: String): AnyRef =
    findField(obj.getClass, name).get(obj)
}
