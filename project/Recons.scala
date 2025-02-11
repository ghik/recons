import com.github.ghik.sbt.nosbt.ProjectGroup
import com.typesafe.sbt.SbtNativePackager.autoImport.*
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.*
import sbt.*
import sbt.Keys.*
import sbtghactions.GenerativePlugin.autoImport.*
import sbtide.Keys.*
import xerial.sbt.Sonatype.autoImport.*

object Recons extends ProjectGroup("recons") {
  val buildClientBinary = taskKey[File]("Builds client binary")

  override def globalSettings: Seq[Def.Setting[?]] = Seq(
    excludeLintKeys ++= Set(ideBasePackages, projectInfo),
  )

  override def buildSettings: Seq[Def.Setting[?]] = Seq(
    crossScalaVersions := Seq(Version.Scala2, Version.Scala3),
    scalaVersion := Version.Scala3,

    // needed by `action-gh-release`
    githubWorkflowPermissions := Some(Permissions.Specify(Map(
      PermissionScope.Contents -> PermissionValue.Write,
    ))),

    githubWorkflowTargetTags ++= Seq("v*"),
    githubWorkflowJavaVersions := Seq("11", "17", "21").map(JavaSpec.temurin),
    githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v"))),

    githubWorkflowPublish := Seq(
      WorkflowStep.Sbt(
        List("ci-release", "+buildClientBinary"),
        env = Map(
          "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
          "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
          "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
          "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}",
        ),
      ),
      WorkflowStep.Use(
        UseRef.Public("softprops", "action-gh-release", "v2"),
        name = Some("Upload client binary"),
        params = Map(
          "files" -> {
            val binaryDir = (client / Universal / target).value.relativeTo(baseDirectory.value).get
            s"${binaryDir.getPath}/*"
          },
        ),
      ),
    ),
  )

  override def commonSettings: Seq[Def.Setting[?]] = Seq(
    crossVersion := CrossVersion.full,

    organization := "com.github.ghik",
    ideBasePackages := Seq("com.github.ghik.recons"),
    Compile / doc / sources := Nil,

    projectInfo := ModuleInfo(
      nameFormal = "Recons",
      description = "Scala Debug Shell",
      homepage = Some(url("https://github.com/ghik/recons")),
      startYear = Some(2024),
      licenses = Vector(
        "Apache License, Version 2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
      ),
      organizationName = "ghik",
      organizationHomepage = Some(url("https://github.com/ghik")),
      scmInfo = Some(ScmInfo(
        browseUrl = url("https://github.com/ghik/recons.git"),
        connection = "scm:git:git@github.com:ghik/recons.git",
        devConnection = Some("scm:git:git@github.com:ghik/recons.git"),
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
      buildClientBinary := (client / Universal / packageBin).value,
    )

  lazy val core = mkSubProject
    .settings(
      libraryDependencies ++= Seq(
        scalaBinaryVersion.value match {
          case "2.13" => "org.scala-lang" % "scala-compiler" % scalaVersion.value
          case "3" => "org.scala-lang" %% "scala3-compiler" % scalaVersion.value
        },
        "org.bouncycastle" % "bcpkix-jdk18on" % Version.BouncyCastle,
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
    .settings(
      Universal / maintainer := "romeqjanoosh@gmail.com",
      Universal / packageName := s"recons-client_${scalaVersion.value}-${version.value}",

      libraryDependencies ++= Seq(
        "commons-cli" % "commons-cli" % Version.CommonsCli,
      ),
    )
}

object Version {
  final val Scala2 = "2.13.14"
  final val Scala3 = "3.4.2"
  final val Scalatest = "3.2.18"
  final val CommonsCli = "1.8.0"
  final val BouncyCastle = "1.78.1"
}
