import com.github.ghik.sbt.nosbt.ProjectGroup
import sbt.*
import sbt.Keys.*
import sbtide.Keys.*

object Scadesh extends ProjectGroup("scadesh") {
  override def commonSettings: Seq[Def.Setting[?]] = Seq(
    scalaVersion := Version.Scala,
    ideBasePackages := Seq("com.github.ghik.scadesh"),

    Compile / scalacOptions ++= Seq(
      "-encoding", "utf-8",
      "-Werror"
    ),

    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % Version.Scalatest % Test
    )
  )

  final val CompileAndTest = "compile->compile;test->test"

  lazy val root = mkRootProject
    .aggregate(core, server, client)
    .settings(
      publish / skip := true
    )

  lazy val core = mkSubProject

  lazy val server = mkSubProject
    .dependsOn(core % CompileAndTest)
    .settings(
      libraryDependencies ++= Seq(
        "org.scala-lang" %% "scala3-compiler" % Version.Scala
      )
    )

  lazy val client = mkSubProject
    .dependsOn(core % CompileAndTest)
}

object Version {
  final val Scala = "3.3.3"
  final val Scalatest = "3.2.18"
}
