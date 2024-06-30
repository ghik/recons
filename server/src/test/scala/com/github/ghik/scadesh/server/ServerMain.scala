package com.github.ghik.scadesh
package server

import java.io.File

object ServerMain {
  def main(args: Array[String]): Unit = {
    val classpath = sys.props("java.class.path").split(File.pathSeparator).toSeq
    new ReplServer(classpath).run()
  }
}
