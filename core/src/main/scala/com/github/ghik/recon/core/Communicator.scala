package com.github.ghik.recon
package core

import java.io._
import java.net.Socket
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object Communicator {
  private val Request = 0: Byte
  private val Response = 1: Byte
  private val Exception = 2: Byte
}
abstract class Communicator(socket: Socket) extends Closeable {
  type InCmd[T] <: Command[T]
  type OutCmd[T] <: Command[T]

  protected implicit def inCmdDecoder[T]: Decoder[InCmd[T]]
  protected implicit def outCmdEncoder[T]: Encoder[OutCmd[T]]

  trait CommandHandler {
    def handleCommand[T](cmd: InCmd[T]): T
  }

  private val dout = new DataOutputStream(socket.getOutputStream)
  private val din = new DataInputStream(socket.getInputStream)

  private var commandHandler = new CommandHandler {
    def handleCommand[T](cmd: InCmd[T]): T =
      throw new UnsupportedOperationException("No command handler set")
  }

  def setCommandHandler(handler: CommandHandler): Unit =
    commandHandler = handler

  private def writeData[T](tpe: Byte, data: T)(implicit e: Encoder[T]): Unit = {
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

  @tailrec private def receive[T]()(implicit d: Decoder[T]): T =
    din.readByte() match {
      case Communicator.Request =>
        doHandleCommand(Decoder.decode[InCmd[T]](din))
        receive[T]()

      case Communicator.Response =>
        Decoder.decode[T](din)

      case Communicator.Exception =>
        throw new CommunicatorException(Decoder.decode[String](din))
    }

  def sendCommand[T](cmd: OutCmd[T]): T = {
    writeData(Communicator.Request, cmd)
    receive()(cmd.responseDecoder)
  }

  def close(): Unit =
    socket.close()
}

class CommunicatorException(message: String) extends IOException(message)
