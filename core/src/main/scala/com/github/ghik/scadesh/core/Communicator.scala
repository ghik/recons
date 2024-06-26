package com.github.ghik.scadesh
package core

import java.io._
import java.net.Socket
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class CommunicatorException(message: String) extends IOException(message)

trait CommandHandler[Cmd[_]] {
  def handleCommand[T](cmd: Cmd[T]): T
}

object Communicator {
  private val Request = 0: Byte
  private val Response = 1: Byte
  private val Exception = 2: Byte
}
final class Communicator[InCmd[T] <: Command[T], OutCmd[T] <: Command[T]](
  socket: Socket,
)(implicit
  inCmdDecoder: Decoder[InCmd[?]],
  outCmdEncoder: Encoder[OutCmd[?]],
) extends Closeable {
  private val dout = new DataOutputStream(socket.getOutputStream)
  private val din = new DataInputStream(socket.getInputStream)

  private var commandHandler = new CommandHandler[InCmd] {
    def handleCommand[T](cmd: InCmd[T]): T =
      throw new UnsupportedOperationException("No command handler set")
  }

  def setCommandHandler(handler: CommandHandler[InCmd]): Unit =
    commandHandler = handler

  private def writeData[T: Encoder](tpe: Byte, data: T): Unit = {
    dout.writeByte(tpe)
    Encoder.encode(dout, data)
    dout.flush()
  }

  private def doHandleCommand[T](cmd: InCmd[T]): Unit = {
    Try(commandHandler.handleCommand(cmd)) match {
      case Success(response) =>
        writeData(Communicator.Response, response)(cmd.responseEncoder)

      case Failure(ex) =>
        val message = Option(ex.getMessage).getOrElse("")
        writeData(Communicator.Exception, message)
    }
  }

  def receiveLoop(): Unit =
    try receive[Nothing]() catch {
      case _: EOFException =>
    }

  @tailrec private def receive[T: Decoder](): T =
    din.readByte() match {
      case Communicator.Request =>
        doHandleCommand(Decoder.decode[InCmd[?]](din))
        receive[T]()

      case Communicator.Response =>
        Decoder.decode[T](din)

      case Communicator.Exception =>
        throw new CommunicatorException(Decoder.decode[String](din))
    }

  def sendCommand[T](cmd: OutCmd[T]): T = {
    writeData[OutCmd[?]](Communicator.Request, cmd)
    receive[T]()(cmd.responseDecoder)
  }

  def close(): Unit =
    socket.close()
}
