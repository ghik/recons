import com.github.ghik.sbt.nosbt.ProjectGroup
import sbt.Keys.*
import sbt.{Def, *}
import sbtide.Keys.*

object Scadesh extends ProjectGroup("scadesh") {
  override def globalSettings: Seq[Def.Setting[?]] = Seq(
    excludeLintKeys += ideBasePackages,
  )

  override def commonSettings: Seq[Def.Setting[?]] = Seq(
    crossVersion := CrossVersion.full,
    crossScalaVersions := Seq(Version.Scala2, Version.Scala3),
    scalaVersion := Version.Scala3,
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
        scalaBinaryVersion.value match {
          case "2.13" => "org.scala-lang" % "scala-compiler" % scalaVersion.value
          case "3" => "org.scala-lang" %% "scala3-compiler" % scalaVersion.value
        },
      ),
    )

  lazy val server = mkSubProject
    .dependsOn(core % CompileAndTest)
    .settings(
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
  final val Scala2 = "2.13.14"
  final val Scala3 = "3.4.2"
  final val Scalatest = "3.2.18"
}
