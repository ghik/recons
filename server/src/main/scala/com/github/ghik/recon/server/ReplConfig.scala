package com.github.ghik.recon
package server

import com.github.ghik.recon.server.utils.Environment

/**
 * Configures a single REPL instance.
 *
 * @param welcome       A welcome message displayed when the REPL starts
 * @param prompt        The prompt string displayed for each input line
 * @param bindings      A map of named bindings (vals) to be made available in the REPL
 * @param initCode      The initial code to be executed in the REPL.
 *                      This is primarily intended for defining default imports of the REPL session.
 *                      The init code may refer to the bindings defined in [[bindings]].
 * @param useExtensions Whether to enable standard
 *                      [[com.github.ghik.recon.server.utils.ShellExtensions ShellExtensions]]
 *                      in the REPL
 */
final case class ReplConfig(
  welcome: String = ReplConfig.DefaultWelcome,
  prompt: String = ReplConfig.DefaultPrompt,
  bindings: Map[String, ReplBinding] = Map.empty,
  initCode: String = "",
  useExtensions: Boolean = true,
) {
  bindings.keys.foreach { name =>
    require(!name.contains('`'), s"Invalid binding name: $name")
  }

  def addBinding(name: String, binding: ReplBinding): ReplConfig =
    copy(bindings = bindings.updated(name, binding))

  def addInitCode(code: String): ReplConfig =
    copy(initCode = s"$initCode\n$code")

  def noExtensions: ReplConfig =
    copy(useExtensions = false)
}
object ReplConfig {
  final val Default = ReplConfig()

  final val DefaultWelcome = {
    import Environment.*

    s"""Welcome to ReCon (Scala Debug Shell), based on Scala $ScalaVersion (Java $JavaVersion, $JavaVmName).
       |Type in expressions for evaluation. Or try :help.
       |""".stripMargin
  }

  final val DefaultPrompt = "recon> "
}
