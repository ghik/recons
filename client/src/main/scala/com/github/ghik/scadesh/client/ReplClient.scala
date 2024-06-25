package com.github.ghik.scadesh
package client

import java.net.Socket

object ReplClient {
  def main(args: Array[String]): Unit = {
    val socket = new Socket("localhost", 6666)
    val terminal = new RemoteJLineTerminal(socket)
    terminal.run()
  }
}
