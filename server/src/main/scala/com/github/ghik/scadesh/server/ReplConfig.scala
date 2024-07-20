package com.github.ghik.scadesh
package server

import com.github.ghik.scadesh.server.utils.Environment

/**
 * Configures a single REPL instance.
 *
 * @param welcome  A welcome message displayed when the REPL starts
 * @param bindings A map of named bindings (vals) to be made available in the REPL
 * @param initCode The initial code to be executed in the REPL.
 *                 This is primarily intended for defining initial imports of the REPL session.
 *                 The init code may refer to the bindings defined in [[bindings]].
 */
final case class ReplConfig(
  welcome: String = ReplConfig.DefaultWelcome,
  bindings: Map[String, ReplBinding] = Map.empty,
  initCode: String = "",
) {
  bindings.keys.foreach { name =>
    require(!name.contains('`'), s"Invalid binding name: $name")
  }
}
object ReplConfig {
  final val Default = ReplConfig()

  final val DefaultWelcome = {
    import Environment.*

    s"""Welcome to Scadesh (Scala Debug Shell), based on Scala $ScalaVersion (Java $JavaVersion, $JavaVmName).
       |Type in expressions for evaluation. Or try :help.
       |""".stripMargin
  }
}
