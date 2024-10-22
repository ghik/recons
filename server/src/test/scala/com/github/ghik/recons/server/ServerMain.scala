package com.github.ghik.recons
package server

import com.github.ghik.recons.core.utils.PkiUtils

import java.io.{File, InputStreamReader, Reader}
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLParameters

object Thinger {
  private def staticMaybe(str: String): String = str.toUpperCase
}
class Thinger {
  private val priv = "priv"

  private def privUpper(arg: String): String = priv + arg.toUpperCase

  def foo: Int = 42
}

object ServerMain {
  def main(args: Array[String]): Unit = {
    val classpath = sys.props("java.class.path").split(File.pathSeparator).toSeq

    val bindings = Map("thinger" -> ReplBinding.forClass(new Thinger))

    val initCode =
      """
        |import java.io._
        |import thinger._
        |""".stripMargin

    def resourceReader(path: String): Reader =
      new InputStreamReader(getClass.getClassLoader.getResourceAsStream(path), StandardCharsets.UTF_8)

    val caCert = PkiUtils.loadPemCert(resourceReader("ca.pem"), "resource ca.pem")
    val serverCert = PkiUtils.loadPemCert(resourceReader("server.pem"), "resource server.pem")
    val serverKey = PkiUtils.loadPemKey(resourceReader("server-key.pem"), "resource server-key.pem")
    val sslContext = PkiUtils.sslContext(
      PkiUtils.keyManagers(Map("server" -> (serverCert, serverKey))),
      PkiUtils.trustManagers(Map("cacert" -> caCert)),
    )

    val sslParams = new SSLParameters
    sslParams.setNeedClientAuth(true)

    new ReplServer(
      classpath,
      tlsConfig = Some(TlsConfig(sslContext, Some(sslParams))),
      replConfig = ReplConfig(bindings = bindings, initCode = initCode),
    ).run()
  }
}
