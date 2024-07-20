package com.github.ghik.scadesh
package server

import javax.net.ssl.{SSLContext, SSLParameters}

/**
 * @param sslContext    an SSL context that will be used to create the server socket
 * @param sslParameters parameters that will be set on the server socket, if provided
 */
final case class TlsConfig(
  sslContext: SSLContext,
  sslParameters: Option[SSLParameters] = None,
)
