package com.github.ghik.scadesh
package server

import java.io.File

object Thinger {
  private def staticMaybe(str: String): String = str.toUpperCase
}
class Thinger {
  private val priv = "priv"

  private def privUpper(arg: String): String = priv.toUpperCase + arg

  def foo: Int = 42
}

object ServerMain {
  def main(args: Array[String]): Unit = {
    val classpath = sys.props("java.class.path").split(File.pathSeparator).toSeq

    val bindings = Map("thinger" -> ReplBinding.forClass(new Thinger))

    val initCode =
      """
        |import java.io._
        |
        |val x = 50
        |val str = x.toString
        |""".stripMargin

    new ReplServer(classpath, bindings = bindings, initCode = initCode).run()
  }
}
