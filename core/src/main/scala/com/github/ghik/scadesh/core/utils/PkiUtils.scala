package com.github.ghik.scadesh
package core.utils

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}

import java.io.FileReader
import java.security.cert.X509Certificate
import java.security.{KeyStore, PrivateKey}
import javax.net.ssl.*
import scala.util.Using

object PkiUtils {
  def loadPemCert(pemFile: String): X509Certificate = {
    val parser = new PEMParser(new FileReader(pemFile))
    val certHolder = Using.resource(parser)(_.readObject()) match {
      case ch: X509CertificateHolder => ch
      case _ => throw new IllegalArgumentException(s"invalid certificate file: $pemFile")
    }
    new JcaX509CertificateConverter().getCertificate(certHolder)
  }

  def loadPemKey(pemFile: String): PrivateKey = {
    val parser = new PEMParser(new FileReader(pemFile))
    val privateKey = Using.resource(parser)(_.readObject()) match {
      case kp: PEMKeyPair => kp.getPrivateKeyInfo
      case pk: PrivateKeyInfo => pk
      case _ => throw new IllegalArgumentException(s"invalid key file: $pemFile")
    }
    new JcaPEMKeyConverter().getPrivateKey(privateKey)
  }

  def keyManagersForSingleCert(cert: X509Certificate, key: PrivateKey): Array[KeyManager] = {
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    val ks = KeyStore.getInstance(KeyStore.getDefaultType)
    ks.load(null, null)
    ks.setKeyEntry("key", key, null, Array(cert))
    kmf.init(ks, null)
    kmf.getKeyManagers
  }

  def trustManagersForSingleCert(cert: X509Certificate): Array[TrustManager] = {
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    val ks = KeyStore.getInstance(KeyStore.getDefaultType)
    ks.load(null, null)
    ks.setCertificateEntry("cert", cert)
    tmf.init(ks)
    tmf.getTrustManagers
  }

  def sslContext(cacert: X509Certificate, cert: X509Certificate, key: PrivateKey): SSLContext = {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(
      keyManagersForSingleCert(cert, key),
      trustManagersForSingleCert(cacert),
      null,
    )
    sslContext
  }
}
