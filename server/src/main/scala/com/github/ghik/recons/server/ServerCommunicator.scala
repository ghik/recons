package com.github.ghik.recons
package server

import com.github.ghik.recons.core._

import java.io.{OutputStream, PrintStream}
import java.net.Socket
import java.nio.charset.StandardCharsets

class ServerCommunicator(socket: Socket) extends Communicator(socket) {
  type InCmd[T] = CompilerCommand[T]
  type OutCmd[T] = TerminalCommand[T]

  protected implicit def inCmdDecoder[T]: Decoder[InCmd[T]] = CompilerCommand.decoder
  protected implicit def outCmdEncoder[T]: Encoder[OutCmd[T]] = TerminalCommand.encoder
}

class CommunicatorOutputStream(comm: ServerCommunicator) extends OutputStream {
  def write(b: Int): Unit =
    write(Array(b.toByte))

  override def write(b: Array[Byte]): Unit =
    comm.sendCommand(TerminalCommand.Write(b))

  override def write(b: Array[Byte], off: Int, len: Int): Unit =
    comm.sendCommand(TerminalCommand.Write(b.slice(off, off + len)))

  override def flush(): Unit =
    comm.sendCommand(TerminalCommand.Flush)
}

class CommunicatorPrintStream(comm: ServerCommunicator)
  extends PrintStream(new CommunicatorOutputStream(comm), true, StandardCharsets.UTF_8) {

  override def print(s: String): Unit =
    comm.sendCommand(TerminalCommand.Write(s.getBytes(StandardCharsets.UTF_8)))

  override def println(x: String): Unit =
    comm.sendCommand(TerminalCommand.Write((x + "\n").getBytes(StandardCharsets.UTF_8)))
}
