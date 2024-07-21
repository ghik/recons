package com.github.ghik.recon
package server

import javax.net.ssl.{SSLContext, SSLParameters}

/**
 * @param sslContext    An SSL context that will be used to create the server socket.
 *                      See [[com.github.ghik.recon.core.utils.PkiUtils PkiUtils]] for utilities
 *                      for creating an `SSLContext` from PEM certificate and key files.
 * @param sslParameters `SSLParameters` to be set on the server socket. You can use this to configure things like
 *                      enabled cipher suites, protocols, force client authentication, etc.
 */
final case class TlsConfig(
  sslContext: SSLContext,
  sslParameters: Option[SSLParameters] = None,
)
