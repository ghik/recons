package com.github.ghik.scadesh
package server

import java.io.File

object ServerMain {
  def main(args: Array[String]): Unit = {
    val classpath = sys.props("java.class.path").split(File.pathSeparator).toSeq

    val initCode =
      """
        |import java.io._
        |
        |val x = 50
        |val str = x.toString
        |""".stripMargin

    new ReplServer(classpath, initCode = initCode).run()
  }
}
