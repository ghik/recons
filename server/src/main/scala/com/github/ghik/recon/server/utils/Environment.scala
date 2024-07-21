package com.github.ghik.recon
package server.utils

object Environment {
  final val ScalaVersion = Compat.Properties.versionNumberString
  final val JavaVersion = Compat.Properties.javaVersion
  final val JavaVmName = Compat.Properties.javaVmName
}
