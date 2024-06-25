package com.github.ghik.scadesh
package server

import com.github.ghik.scadesh.core.{Command, Communicator}

import java.io.{OutputStream, PrintStream}
import java.nio.charset.StandardCharsets

class CommunicatorOutputStream(comm: Communicator) extends OutputStream {
  def write(b: Int): Unit =
    write(Array(b.toByte))

  override def write(b: Array[Byte]): Unit =
    comm.sendCommand(Command.Write(b))

  override def write(b: Array[Byte], off: Int, len: Int): Unit =
    comm.sendCommand(Command.Write(b.slice(off, off + len)))

  override def flush(): Unit =
    comm.sendCommand(Command.Flush)
}

class CommunicatorPrintStream(comm: Communicator)
  extends PrintStream(new CommunicatorOutputStream(comm), true, StandardCharsets.UTF_8) {

  override def print(s: String): Unit =
    comm.sendCommand(Command.Write(s.getBytes(StandardCharsets.UTF_8)))

  override def println(x: String): Unit =
    comm.sendCommand(Command.Write((x + "\n").getBytes(StandardCharsets.UTF_8)))
}
