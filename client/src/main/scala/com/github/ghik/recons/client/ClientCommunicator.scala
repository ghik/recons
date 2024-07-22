package com.github.ghik.recons
package client

import com.github.ghik.recons.core._

import java.net.Socket

final class ClientCommunicator(socket: Socket) extends Communicator(socket) {
  type InCmd[T] = TerminalCommand[T]
  type OutCmd[T] = CompilerCommand[T]

  protected implicit def inCmdDecoder[T]: Decoder[InCmd[T]] = TerminalCommand.decoder
  protected implicit def outCmdEncoder[T]: Encoder[OutCmd[T]] = CompilerCommand.encoder
}
