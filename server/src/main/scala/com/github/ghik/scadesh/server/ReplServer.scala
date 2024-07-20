package com.github.ghik.scadesh
package server

import com.github.ghik.scadesh.core.CommonDefaults

import java.io.{Closeable, File}
import java.net.{InetAddress, ServerSocket, Socket, SocketException}
import javax.net.ssl.SSLServerSocket
import scala.util.{Failure, Success, Try}

final class ReplServer(
  classpath: Seq[String],
  tlsConfig: Option[TlsConfig],
  bindAddress: String = CommonDefaults.DefaultAddress,
  bindPort: Int = CommonDefaults.DefaultPort,
  replConfig: ReplConfig = ReplConfig.Default,
) extends Closeable {

  private val bindAddr = InetAddress.getByName(bindAddress)

  private val socket: ServerSocket = tlsConfig match {
    case None =>
      new ServerSocket(bindPort, 0, bindAddr)
    case Some(tlsConfig) =>
      val sock = tlsConfig.sslContext.getServerSocketFactory
        .createServerSocket(bindPort, 0, bindAddr)
        .asInstanceOf[SSLServerSocket]
      tlsConfig.sslParameters.foreach(sock.setSSLParameters)
      sock
  }

  def run(): Unit = {
    while (!socket.isClosed) {
      Try(socket.accept()) match {
        case Success(client) => new ReplThread(client).start()
        case Failure(_: SocketException) if socket.isClosed => // socket was closed, don't propagate exception
        case Failure(e) => throw e
      }
    }
  }

  def close(): Unit =
    socket.close()

  private class ReplThread(client: Socket) extends Thread {
    override def run(): Unit = {
      val settings = Array("-cp", classpath.mkString(File.pathSeparator))
      RemoteReplRunner.run(settings, client, replConfig)
    }
  }
}
