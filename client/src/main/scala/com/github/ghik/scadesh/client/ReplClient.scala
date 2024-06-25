package com.github.ghik.scadesh
package client

import java.net.Socket

object ReplClient {
  def main(args: Array[String]): Unit = {
    val host = args.applyOrElse(0, (_: Int) => "localhost")
    val port = args.applyOrElse(1, (_: Int) => "6666").toInt
    val socket = new Socket(host, port)
    val terminal = new RemoteJLineTerminal(socket)
    terminal.run()
  }
}
