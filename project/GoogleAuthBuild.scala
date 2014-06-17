import sbt._
import Keys._
import play.Project._

object GoogleAuthBuild extends Build {

  val playVersion = "2.2.0"

  lazy val baseSettings = Seq(
    version            := "0.0.1-SNAPSHOT",
    scalaVersion       := "2.10.3",
    scalaBinaryVersion := "2.10",
    organization       := "com.gu",
    resolvers ++= Seq(
      "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases"
    )
  )

  val appName = "play-googleauth"

  lazy val googleauth = Project(appName, base = file("module"))
    .settings(baseSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.play" %% "play" % playVersion % "provided",
        "commons-codec" % "commons-codec" % "1.9"
      ),
      name                    := appName,
      publishMavenStyle       := true,
      publishArtifact in Test := false,
      pomIncludeRepository    := { _ => false },
      publishTo               := {
        val nexus = "https://oss.sonatype.org/"
        if (isSnapshot.value)
          Some("snapshots" at nexus + "content/repositories/snapshots")
        else
          Some("releases"  at nexus + "service/local/staging/deploy/maven2")
      },
      licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),
      homepage := Some(url("https://github.com/guardian/play-googleauth")),
      pomExtra := {
        <scm>
          <url>git@github.com:guardian/play-googleauth.git</url>
          <connection>scm:git:git@github.com:guardian/play-googleauth.git</connection>
        </scm>
        <developers>
          <developer>
            <id>sihil</id>
            <name>Simon Hildrew</name>
            <url>https://github.com/sihil</url>
          </developer>
        </developers>
      }
    )

  lazy val root = Project("googleauth", base = file("."))
    .settings(baseSettings: _*).aggregate(googleauth)
