package com.github.ghik.recons
package core.utils

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser, X509TrustedCertificateBlock}

import java.io.{File, FileReader, IOException, Reader}
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate
import java.security.{KeyStore, PrivateKey}
import javax.net.ssl.*
import scala.util.Using

object PkiUtils {
  /**
   * Loads an X.509 certificate from a PEM file.
   */
  def loadPemCert(file: File): X509Certificate =
    loadPemCert(new FileReader(file, StandardCharsets.UTF_8), s"file ${file.getPath}")

  /**
   * Loads an X.509 certificate from a reader, assuming it's in PEM format.
   *
   * @param resourceClue a name or short description of the resource, to be included into error messages
   */
  def loadPemCert(reader: Reader, resourceClue: String): X509Certificate = {
    val certHolder = Using.resource(new PEMParser(reader))(_.readObject()) match {
      case ch: X509CertificateHolder => ch
      case tcb: X509TrustedCertificateBlock => tcb.getCertificateHolder
      case _ => throw new IOException(s"invalid certificate $resourceClue")
    }
    new JcaX509CertificateConverter().getCertificate(certHolder)
  }

  /**
   * Loads a private key from a PEM file.
   * The content of the file may be a bare private key (e.g. `PRIVATE KEY`) or a key
   * pair (e.g. `RSA/DSA/EC PRIVATE KEY`)
   */
  def loadPemKey(file: File): PrivateKey =
    loadPemKey(new FileReader(file, StandardCharsets.UTF_8), s"file ${file.getPath}")

  /**
   * Loads a private key from a reader, assuming it's in PEM format.
   * The content of the resource may be a bare private key (e.g. `PRIVATE KEY`) or a key
   * pair (e.g. `RSA/DSA/EC PRIVATE KEY`)
   *
   * @param resourceClue a name or short description of the resource, to be included into error messages
   */
  def loadPemKey(reader: Reader, resourceClue: String): PrivateKey = {
    val privateKey = Using.resource(new PEMParser(reader))(_.readObject()) match {
      case kp: PEMKeyPair => kp.getPrivateKeyInfo
      case pk: PrivateKeyInfo => pk
      case _ => throw new IOException(s"invalid private key or key-pair $resourceClue")
    }
    new JcaPEMKeyConverter().getPrivateKey(privateKey)
  }

  /**
   * Creates an array of `KeyManager`s from a map of aliases to certificate-key pairs.
   * The returned array is intended to be passed to `SSLContext.init`.
   */
  def keyManagers(keys: Map[String, (X509Certificate, PrivateKey)]): Array[KeyManager] = {
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    val ks = KeyStore.getInstance(KeyStore.getDefaultType)
    ks.load(null, null)
    keys.foreach { case (alias, (cert, key)) =>
      ks.setKeyEntry(alias, key, null, Array(cert))
    }
    kmf.init(ks, null)
    kmf.getKeyManagers
  }

  /**
   * Creates an array of `TrustManagers`s from a map of aliases to certificates.
   * The returned array is intended to be passed to `SSLContext.init`.
   */
  def trustManagers(certs: Map[String, X509Certificate]): Array[TrustManager] = {
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    val ks = KeyStore.getInstance(KeyStore.getDefaultType)
    ks.load(null, null)
    certs.foreach { case (alias, cert) =>
      ks.setCertificateEntry(alias, cert)
    }
    tmf.init(ks)
    tmf.getTrustManagers
  }

  /**
   * Creates an `SSLContext` with the given key and trust managers.
   */
  def sslContext(
    keyManagers: Array[KeyManager],
    trustManagers: Array[TrustManager],
  ): SSLContext = {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagers, trustManagers, null)
    sslContext
  }
}
