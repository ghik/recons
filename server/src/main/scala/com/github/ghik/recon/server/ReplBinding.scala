package com.github.ghik.recon
package server

import scala.reflect.{ClassTag, classTag}

final case class ReplBinding(staticType: String, value: Any)
object ReplBinding {
  def forClass[T: ClassTag](value: T): ReplBinding =
    ReplBinding(s"_root_.${classTag[T].runtimeClass.getName}", value)
}
