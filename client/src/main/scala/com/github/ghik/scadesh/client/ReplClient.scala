package com.github.ghik.scadesh
package client

import com.github.ghik.scadesh.core.CommonDefaults
import com.github.ghik.scadesh.core.utils.PkiUtils
import org.apache.commons.cli.{DefaultParser, Options, Option as CliOption}

import java.net.Socket
import java.security.PrivateKey
import java.security.cert.X509Certificate

object ReplClient {
  private object Options {
    val host: CliOption = CliOption.builder("h").longOpt("host").hasArg
      .desc("host to connect to").build

    val port: CliOption = CliOption.builder("p").longOpt("port").hasArg
      .converter(Integer.parseInt)
      .desc("port to connect to").build

    val noTls: CliOption = CliOption.builder().longOpt("no-tls")
      .desc("use plain TCP").build

    val cacert: CliOption = CliOption.builder().longOpt("cacert").hasArg
      .converter(PkiUtils.loadPemCert)
      .desc("path to CA certificate PEM file").build

    val cert: CliOption = CliOption.builder().longOpt("cert").hasArg
      .converter(PkiUtils.loadPemCert)
      .desc("path to client certificate PEM file").build

    val key: CliOption = CliOption.builder().longOpt("key").hasArg
      .converter(PkiUtils.loadPemKey)
      .desc("path to client private key PEM file").build
  }

  private val options = new Options()
    .addOption(Options.host)
    .addOption(Options.port)
    .addOption(Options.noTls)
    .addOption(Options.cacert)
    .addOption(Options.cert)
    .addOption(Options.key)


  def main(args: Array[String]): Unit = {
    val cmdLine = new DefaultParser().parse(options, args)

    val noTls = cmdLine.hasOption(Options.noTls)
    val host = cmdLine.getOptionValue(Options.host, CommonDefaults.DefaultAddress)
    val port = cmdLine.getParsedOptionValue[Int](Options.port, CommonDefaults.DefaultPort)

    val socket =
      if (noTls) new Socket(host, port)
      else {
        val cacert = cmdLine.getParsedOptionValue[X509Certificate](Options.cacert)
        val cert = cmdLine.getParsedOptionValue[X509Certificate](Options.cert)
        val key = cmdLine.getParsedOptionValue[PrivateKey](Options.key)
        val sslContext = PkiUtils.sslContext(
          if (cert != null && key != null) PkiUtils.keyManagersForSingleCert(cert, key) else null,
          if (cert != null) PkiUtils.trustManagersForSingleCert(cacert) else null,
        )
        sslContext.getSocketFactory.createSocket(host, port)
      }

    TerminalRunner.run(socket)
  }
}
