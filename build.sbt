import ReleaseTransformations._
import Dependencies._

name := "play-googleauth"

val sonatypeReleaseSettings = Seq(
  organization := "com.gu.play-googleauth",

  description := "Simple Google authentication module for Play 2",

  licenses := Seq("Apache V2" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),

  releaseCrossBuild := true, // true if you cross-build the project for multiple Scala versions
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    // For non cross-build projects, use releaseStepCommand("publishSigned")
    releaseStepCommandAndRemaining("+publishSigned"),
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
)

def projectWithPlayVersion(majorMinorVersion: String) =
  Project(s"play-v$majorMinorVersion", file(s"play-v$majorMinorVersion")).settings(
    scalaVersion       := "2.12.16",
    crossScalaVersions := Seq(scalaVersion.value, "2.13.8"),
    scalacOptions ++= Seq("-feature", "-deprecation"),

    libraryDependencies ++= Seq(
      "com.gu.play-secret-rotation" %% "core" % "0.35",
      "org.typelevel" %% "cats-core" % "2.8.0",
      commonsCodec,
      "org.scalatest" %% "scalatest" % "3.2.12" % Test,
      "com.typesafe.akka" %% "akka-http-core" % "10.2.10" % Test
    ) ++ googleDirectoryAPI ++ playLibs(majorMinorVersion),

    sonatypeReleaseSettings
  )

lazy val `play-v27` = projectWithPlayVersion("27")
lazy val `play-v28` = projectWithPlayVersion("28")

lazy val `play-googleauth-root` = (project in file(".")).aggregate(
  `play-v27`,
  `play-v28`
).settings(
  publishArtifact := false,
  publish/skip := true,

  sonatypeReleaseSettings
)
