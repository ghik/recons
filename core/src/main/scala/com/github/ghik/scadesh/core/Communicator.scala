package com.github.ghik.scadesh
package core

import io.bullet.borer.{Cbor, Decoder}

import java.io.*
import java.net.Socket
import java.nio.charset.StandardCharsets
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class CommunicatorException(message: String) extends IOException(message)

trait CommandHandler {
  def handleCommand[T](cmd: Command[T]): T
}

object Communicator {
  private val Request = 0: Byte
  private val Response = 1: Byte
  private val Exception = 2: Byte
}
final class Communicator(socket: Socket) extends Closeable {
  private var curId = 0
  private val dout = new DataOutputStream(socket.getOutputStream)
  private val din = new DataInputStream(socket.getInputStream)

  private def nextId(): Int = {
    curId += 1
    curId
  }

  private var commandHandler = new CommandHandler {
    def handleCommand[T](cmd: Command[T]): T =
      throw new UnsupportedOperationException("No command handler set")
  }

  def setCommandHandler(handler: CommandHandler): Unit =
    commandHandler = handler

  private def readData(): (Int, Array[Byte]) = {
    val id = din.readInt()
    val len = din.readInt()
    val bytes = new Array[Byte](len)
    din.readFully(bytes)
    (id, bytes)
  }

  private def writeData(tpe: Byte, id: Int, bytes: Array[Byte]): Unit = {
    dout.writeByte(tpe)
    dout.writeInt(id)
    dout.writeInt(bytes.length)
    dout.write(bytes)
    dout.flush()
  }

  private def doHandleCommand[T](id: Int, cmd: Command[T]): Unit = {
    Try(commandHandler.handleCommand(cmd)) match {
      case Success(response) =>
        val responseBytes = Cbor.encode(response)(using cmd.responseEncoder).toByteArray
        writeData(Communicator.Response, id, responseBytes)

      case Failure(ex) =>
        val responseBytes = Option(ex.getMessage).getOrElse("").getBytes(StandardCharsets.UTF_8)
        writeData(Communicator.Exception, id, responseBytes)
    }
  }

  def receiveLoop(): Unit =
    try receive(-1) catch {
      case _: EOFException =>
    }

  @tailrec private def receive(reqId: Int): Array[Byte] =
    din.readByte() match {
      case Communicator.Request =>
        val (id, bytes) = readData()
        val cmd = Cbor.decode(bytes).to[Command[?]].value
        doHandleCommand(id, cmd)
        receive(reqId)

      case Communicator.Response =>
        val (id, bytes) = readData()
        if (id == reqId) bytes
        else receive(reqId)

      case Communicator.Exception =>
        val (id, bytes) = readData()
        val msg = new String(bytes, StandardCharsets.UTF_8)
        if (id == reqId) throw new CommunicatorException(msg)
        else receive(reqId)
    }

  def sendCommand[T: Decoder](cmd: Command[T]): T = {
    val reqId = nextId()
    writeData(Communicator.Request, reqId, Cbor.encode[Command[?]](cmd).toByteArray)
    val respBytes = receive(reqId)
    Cbor.decode(respBytes).to[T].value
  }

  def close(): Unit =
    socket.close()
}
