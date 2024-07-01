package com.github.ghik.scadesh
package server

import scala.reflect.{ClassTag, classTag}
import scala.util.DynamicVariable

final case class ReplBinding(staticType: String, value: Any)
object ReplBinding {
  def forClass[T: ClassTag](value: T): ReplBinding =
    ReplBinding(s"_root_.${classTag[T].runtimeClass.getName}", value)

  private val className = classOf[ReplBinding].getName
  private val bindingsDV = new DynamicVariable[Map[String, ReplBinding]](Map.empty)

  def get[T](name: String): T =
    bindingsDV.value(name).value.asInstanceOf[T]

  private[server] def withBindings[T](bindings: Map[String, ReplBinding])(body: => T): T =
    bindingsDV.withValue(bindings)(body)

  private def escape(str: String): String = str.flatMap {
    case '\b' => "\\b"
    case '\t' => "\\t"
    case '\n' => "\\n"
    case '\f' => "\\f"
    case '\r' => "\\r"
    case '\\' => "\\\\"
    case '\"' => "\\\""
    case '\'' => "\\\'"
    case c => c.toString
  }

  private[server] def declarations(bindings: Map[String, ReplBinding]): String =
    bindings.map { case (name, ReplBinding(staticType, _)) =>
      s"val `$name`: $staticType = _root_.$className.get[$staticType](\"${escape(name)}\")"
    }.mkString("\n")
}
