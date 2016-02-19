import ReleaseTransformations._
import Dependencies._

name               := "play-googleauth"

organization       := "com.gu"

scalaVersion       := "2.11.7"

crossScalaVersions := Seq("2.10.6", scalaVersion.value)

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  play % "provided",
  playWS % "provided",
  commonsCodec,
  googleDataAPI
) ++ googleDirectoryAPI

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

releaseCrossBuild := true

releasePublishArtifactsAction := PgpKeys.publishSigned.value

releaseProcess := Seq(
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)
