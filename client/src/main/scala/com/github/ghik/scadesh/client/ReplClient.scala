package com.github.ghik.scadesh
package client

import org.apache.commons.cli.{DefaultParser, Options, Option as CliOption}

import java.net.Socket

object ReplClient {
  private val hostOption = new CliOption("h", "host", true, "host to connect to")
  private val portOption = new CliOption("p", "port", true, "port to connect to")

  private val options = new Options()
    .addOption(hostOption)
    .addOption(portOption)

  def main(args: Array[String]): Unit = {
    val cmdLine = new DefaultParser().parse(options, args)

    val host = cmdLine.getOptionValue(hostOption, "localhost")
    val port = cmdLine.getOptionValue(portOption, "6666").toInt
    val socket = new Socket(host, port)
    TerminalRunner.run(socket)
  }
}
