package com.github.ghik.scadesh
package server

import java.io.File

class Thinger {
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
