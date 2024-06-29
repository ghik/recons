package com.github.ghik.scadesh
package server

import java.io.{Closeable, File}
import java.net.{ServerSocket, Socket, SocketException}
import scala.util.{Failure, Success, Try}

class ReplServer(
  classpath: Seq[String],
  port: Int = 6666,
) extends Closeable {
  private val socket = new ServerSocket(port)

  def run(): Unit = {
    while (!socket.isClosed) {
      Try(socket.accept()) match {
        case Success(client) => new ReplThread(client, classpath).start()
        case Failure(_: SocketException) if socket.isClosed => // socket was closed, don't propagate exception
        case Failure(e) => throw e
      }
    }
  }

  def close(): Unit =
    socket.close()
}
object ReplServer {
  def main(args: Array[String]): Unit = {
    val classpath = sys.props("java.class.path").split(File.pathSeparator).toSeq
    new ReplServer(classpath).run()
  }
}

class ReplThread(client: Socket, classpath: Seq[String]) extends Thread {
  override def run(): Unit = {
    val settings = Array("-cp", classpath.mkString(File.pathSeparator))
    RemoteReplRunner.run(settings, client)
  }
}
