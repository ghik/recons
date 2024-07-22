package com.github.ghik.recons
package server.utils

object Environment {
  final val ScalaVersion = Compat.Properties.versionNumberString
  final val JavaVersion = Compat.Properties.javaVersion
  final val JavaVmName = Compat.Properties.javaVmName
}
