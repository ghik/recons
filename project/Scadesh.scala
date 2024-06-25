import com.github.ghik.sbt.nosbt.ProjectGroup
import sbt.Keys.*
import sbt.{Def, *}
import sbtide.Keys.*

object Scadesh extends ProjectGroup("scadesh") {
  override def globalSettings: Seq[Def.Setting[?]] = Seq(
    excludeLintKeys += ideBasePackages,
  )

  override def commonSettings: Seq[Def.Setting[?]] = Seq(
    scalaVersion := Version.Scala,
    ideBasePackages := Seq("com.github.ghik.scadesh"),

    Compile / scalacOptions ++= Seq(
      "-encoding", "utf-8",
      "-deprecation",
      "-Werror",
    ),

    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % Version.Scalatest % Test,
    ),
  )

  final val CompileAndTest = "compile->compile;test->test"

  lazy val root = mkRootProject
    .aggregate(core, server, client)
    .settings(
      publish / skip := true,
    )

  lazy val core = mkSubProject
    .settings(
      libraryDependencies ++= Seq(
        "org.jline" % "jline-terminal" % Version.JLine,
        "org.jline" % "jline-reader" % Version.JLine,
        "io.bullet" %% "borer-core" % Version.Borer,
        "io.bullet" %% "borer-derivation" % Version.Borer,
      ),
    )

  lazy val server = mkSubProject
    .dependsOn(core % CompileAndTest)
    .settings(
      libraryDependencies ++= Seq(
        "org.scala-lang" %% "scala3-compiler" % Version.Scala,
      ),

      Test / resourceGenerators += Def.task {
        val file = (Test / resourceManaged).value / "classpath"
        val classpath = (Compile / fullClasspath).value
        val contents = classpath.map(_.data.getAbsolutePath).mkString("\n")
        IO.write(file, contents)
        Seq(file)
      },
    )

  lazy val client = mkSubProject
    .dependsOn(core % CompileAndTest)
}

object Version {
  final val Scala = "3.3.3"
  final val JLine = "3.19.0" // needs to be in sync with compiler dependency
  final val Borer = "1.14.0"
  final val Scalatest = "3.2.18"
}
