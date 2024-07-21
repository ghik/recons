package com.github.ghik.scadesh
package server

import com.github.ghik.scadesh.core.CommonDefaults

import java.io.{Closeable, File}
import java.net.{InetAddress, ServerSocket, Socket, SocketException}
import javax.net.ssl.SSLServerSocket
import scala.util.{Failure, Success, Try}

/**
 * A remote Scala REPL server. Each incoming connection, and a REPL session associated with it, 
 * is handled by a separate thread.
 *
 * @param classpath   The classpath used by REPL's Scala compiler.
 *                    It must contain all the classes and APIs that you want to use in the REPL.
 *                    Often the simplest way to obtain it is to parse the `java.class.path` system property.
 * @param tlsConfig   TLS configuration. If `None`, the server will use plain, unencrypted TCP.
 * @param bindAddress The address to bind to. The default is 127.0.0.1.
 * @param bindPort    The port to bind to. The default is 6666.
 * @param backlog     The maximum number of pending connections. The value 0 indicates a system default.
 * @param replConfig  Additional configuration for a REPL session.
 */
final class ReplServer(
  classpath: Seq[String],
  tlsConfig: Option[TlsConfig],
  bindAddress: String = CommonDefaults.DefaultAddress,
  bindPort: Int = CommonDefaults.DefaultPort,
  backlog: Int = 0,
  replConfig: ReplConfig = ReplConfig.Default,
) extends Closeable {

  private val bindAddr = InetAddress.getByName(bindAddress)

  private val socket: ServerSocket = tlsConfig match {
    case None =>
      new ServerSocket(bindPort, backlog, bindAddr)
    case Some(tlsConfig) =>
      val sock = tlsConfig.sslContext.getServerSocketFactory
        .createServerSocket(bindPort, backlog, bindAddr)
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
