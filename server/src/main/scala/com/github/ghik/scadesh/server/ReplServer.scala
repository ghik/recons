package com.github.ghik.scadesh
package server

import java.io.{Closeable, File}
import java.net.{ServerSocket, Socket, SocketException}
import scala.util.{Failure, Success, Try}

class ReplServer(
  classpath: Seq[String],
  port: Int = ReplServer.DefaultPort,
  initCode: String = "",
) extends Closeable {
  private val socket = new ServerSocket(port)

  def run(): Unit = {
    while (!socket.isClosed) {
      Try(socket.accept()) match {
        case Success(client) => new ReplThread(client, classpath, initCode).start()
        case Failure(_: SocketException) if socket.isClosed => // socket was closed, don't propagate exception
        case Failure(e) => throw e
      }
    }
  }

  def close(): Unit =
    socket.close()
}
object ReplServer {
  final val DefaultPort = 6666
}

class ReplThread(client: Socket, classpath: Seq[String], initCode: String) extends Thread {
  override def run(): Unit = {
    val settings = Array("-cp", classpath.mkString(File.pathSeparator))
    RemoteReplRunner.run(settings, initCode, client)
  }
}
