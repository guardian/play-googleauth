import sbtrelease._
import ReleaseStateTransformations._

releaseSettings

sonatypeSettings

name               := "play-googleauth"

organization       := "com.gu"

scalaVersion       := "2.11.6"

crossScalaVersions := Seq("2.10.5", scalaVersion.value)

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % "2.3.8" % "provided",
  "com.typesafe.play" %% "play-ws" % "2.3.8" % "provided",
  "commons-codec" % "commons-codec" % "1.9",
  "com.google.apis" % "google-api-services-admin-directory" % "directory_v1-rev53-1.20.0",
  "com.google.gdata" % "core" % "1.47.1"
)

scalacOptions ++= Seq("-feature", "-deprecation")

description        := "Simple Google authentication module for Play 2"

licenses := Seq("Apache V2" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))

scmInfo := Some(ScmInfo(
  url("https://github.com/guardian/play-googleauth"),
  "scm:git:git@github.com:guardian/play-googleauth.git"
))

pomExtra := {
  <url>https://github.com/guardian/play-googleauth</url>
  <developers>
    <developer>
      <id>sihil</id>
      <name>Simon Hildrew</name>
      <url>https://github.com/sihil</url>
    </developer>
  </developers>
}

ReleaseKeys.crossBuild := true

ReleaseKeys.releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(
    action = state => Project.extract(state).runTask(PgpKeys.publishSigned, state)._1,
    enableCrossBuild = true
  ),
  setNextVersion,
  commitNextVersion,
  ReleaseStep(state => Project.extract(state).runTask(SonatypeKeys.sonatypeReleaseAll, state)._1),
  pushChanges
)
