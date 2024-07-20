package com.github.ghik.scadesh
package server

import java.io.{Closeable, File}
import java.net.{InetAddress, ServerSocket, Socket, SocketException}
import scala.util.{Failure, Success, Try}

class ReplServer(
  classpath: Seq[String],
  bindAddress: String = ReplServer.DefaultBindAddress,
  bindPort: Int = ReplServer.DefaultPort,
  initCode: String = "",
  bindings: Map[String, ReplBinding] = Map.empty,
) extends Closeable {
  bindings.keys.foreach { name =>
    require(!name.contains('`'), s"Invalid binding name: $name")
  }

  private val socket = new ServerSocket(bindPort, 0, InetAddress.getByName(bindAddress))

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
      RemoteReplRunner.run(settings, client, bindings, initCode)
    }
  }
}
object ReplServer {
  final val DefaultBindAddress = "127.0.0.1"
  final val DefaultPort = 6666
}
