package com.github.ghik.scadesh
package client

import com.github.ghik.scadesh.core.CommonDefaults
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

    val host = cmdLine.getOptionValue(hostOption, CommonDefaults.DefaultAddress)
    val port = Option(cmdLine.getOptionValue(portOption)).fold(CommonDefaults.DefaultPort)(_.toInt)
    val socket = new Socket(host, port)
    TerminalRunner.run(socket)
  }
}
