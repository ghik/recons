import com.github.ghik.sbt.nosbt.ProjectGroup
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import sbt.*
import sbt.Keys.*
import sbtghactions.GenerativePlugin.autoImport.*
import sbtide.Keys.*
import xerial.sbt.Sonatype.autoImport.*

object Scadesh extends ProjectGroup("scadesh") {
  override def globalSettings: Seq[Def.Setting[?]] = Seq(
    excludeLintKeys ++= Set(ideBasePackages, projectInfo),
  )

  override def buildSettings: Seq[Def.Setting[?]] = Seq(
    crossScalaVersions := Seq(Version.Scala2, Version.Scala3),
    scalaVersion := Version.Scala3,

    githubWorkflowTargetTags ++= Seq("v*"),
    githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17")),
    githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v"))),

    githubWorkflowPublish := Seq(WorkflowStep.Sbt(
      List("ci-release"),
      env = Map(
        "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
        "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
        "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
        "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}",
      ),
    )),
  )

  override def commonSettings: Seq[Def.Setting[?]] = Seq(
    crossVersion := CrossVersion.full,

    organization := "com.github.ghik",
    ideBasePackages := Seq("com.github.ghik.scadesh"),
    Compile / doc / sources := Nil,

    projectInfo := ModuleInfo(
      nameFormal = "Scadesh",
      description = "Scala Debug Shell",
      homepage = Some(url("https://github.com/ghik/scadesh")),
      startYear = Some(2024),
      licenses = Vector(
        "Apache License, Version 2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
      ),
      organizationName = "ghik",
      organizationHomepage = Some(url("https://github.com/ghik")),
      scmInfo = Some(ScmInfo(
        browseUrl = url("https://github.com/ghik/scadesh.git"),
        connection = "scm:git:git@github.com:ghik/scadesh.git",
        devConnection = Some("scm:git:git@github.com:ghik/scadesh.git"),
      )),
      developers = Vector(
        Developer("ghik", "Roman Janusz", "romeqjanoosh@gmail.com", url("https://github.com/ghik")),
      ),
    ),

    Compile / scalacOptions ++= Seq(
      "-encoding", "utf-8",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings",
    ),
    Compile / scalacOptions ++= {
      scalaBinaryVersion.value match {
        case "2.13" => Seq("-Xsource:3")
        case "3" => Seq()
      }
    },

    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
    publishTo := sonatypePublishToBundle.value,

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
    .enablePlugins(JavaAppPackaging)
}

object Version {
  final val Scala2 = "2.13.14"
  final val Scala3 = "3.4.2"
  final val Scalatest = "3.2.18"
}
