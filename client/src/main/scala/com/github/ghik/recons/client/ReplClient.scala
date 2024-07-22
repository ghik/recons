package com.github.ghik.recons
package client

import com.github.ghik.recons.core.CommonDefaults
import com.github.ghik.recons.core.utils.PkiUtils
import org.apache.commons.cli.{DefaultParser, HelpFormatter, MissingOptionException, Options, Option as CliOption}

import java.io.File
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

    val noTls: CliOption = CliOption.builder.longOpt("no-tls")
      .desc("use plain TCP").build

    val cacert: CliOption = CliOption.builder.longOpt("cacert").hasArg
      .converter(file => PkiUtils.loadPemCert(new File(file)))
      .desc("path to CA certificate PEM file").build

    val cert: CliOption = CliOption.builder.longOpt("cert").hasArg
      .converter(file => PkiUtils.loadPemCert(new File(file)))
      .desc("path to client certificate PEM file").build

    val key: CliOption = CliOption.builder.longOpt("key").hasArg
      .converter(file => PkiUtils.loadPemKey(new File(file)))
      .desc("path to client private key PEM file").build

    val help: CliOption = CliOption.builder.longOpt("help")
      .desc("show this help").build
  }

  private val options = new Options()
    .addOption(Options.host)
    .addOption(Options.port)
    .addOption(Options.noTls)
    .addOption(Options.cacert)
    .addOption(Options.cert)
    .addOption(Options.key)
    .addOption(Options.help)


  def main(args: Array[String]): Unit = {
    val cmdLine = new DefaultParser().parse(options, args)

    if (cmdLine.hasOption(Options.help)) {
      new HelpFormatter().printHelp("recons-client", options)
    } else {
      val noTls = cmdLine.hasOption(Options.noTls)
      val host = cmdLine.getOptionValue(Options.host, CommonDefaults.DefaultAddress)
      val port = cmdLine.getParsedOptionValue[Int](Options.port, CommonDefaults.DefaultPort)

      val socket =
        if (noTls) new Socket(host, port)
        else {
          val cacert = cmdLine.getParsedOptionValue[X509Certificate](Options.cacert,
            () => throw new MissingOptionException("either --no-tls or --cacert must be specified"))

          val cert = cmdLine.getParsedOptionValue[X509Certificate](Options.cert)
          val key = cmdLine.getParsedOptionValue[PrivateKey](Options.key)

          val certKeyPairs: Map[String, (X509Certificate, PrivateKey)] = (cert, key) match {
            case (null, null) => Map.empty
            case (null, _) => throw new MissingOptionException("--cert must be specified along --key")
            case (_, null) => throw new MissingOptionException("--key must be specified along --cert")
            case _ => Map("client" -> (cert, key))
          }

          val sslContext = PkiUtils.sslContext(
            PkiUtils.keyManagers(certKeyPairs),
            PkiUtils.trustManagers(Map("ca" -> cacert)),
          )
          sslContext.getSocketFactory.createSocket(host, port)
        }

      TerminalRunner.run(socket)
    }
  }
}
