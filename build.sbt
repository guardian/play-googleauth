import ReleaseTransformations.*
import Dependencies.*
import sbtversionpolicy.withsbtrelease.ReleaseVersion.fromAggregatedAssessedCompatibilityWithLatestRelease

name := "play-googleauth"

ThisBuild / scalaVersion := "2.13.18"

val artifactPomMetadataSettings = Seq(
  organization := "com.gu.play-googleauth",
  licenses := Seq(License.Apache2),
  description := "Simple Google authentication module for the Play web framework"
)

val jjwtVersion = "0.13.0"

def projectWithPlayVersion(playVersion: PlayVersion) =
  Project(playVersion.projectId, file(playVersion.projectId)).settings(
    crossScalaVersions := Seq(scalaVersion.value, "3.3.7"),
    scalacOptions ++= Seq("-feature", "-deprecation", "-release","11"),

    libraryDependencies ++= Seq(
      "com.gu.play-secret-rotation" %% "core" % "17.0.4",
      "org.typelevel" %% "cats-core" % "2.13.0",
      "io.jsonwebtoken" % "jjwt-api" % jjwtVersion,
      "io.jsonwebtoken" % "jjwt-impl" % jjwtVersion,
      "io.jsonwebtoken" % "jjwt-jackson" % jjwtVersion,
      commonsCodec,
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "software.amazon.awssdk" % "ssm" % "2.42.30" % Test
    ) ++ googleDirectoryAPI ++ playVersion.playLibs,

    artifactPomMetadataSettings
  )

lazy val `play-v30` = projectWithPlayVersion(PlayVersion.V30)

lazy val `play-googleauth-root` = (project in file(".")).aggregate(
  `play-v30`
).settings(
  publish / skip := true,

  releaseVersion := fromAggregatedAssessedCompatibilityWithLatestRelease().value,
  releaseCrossBuild := true, // true if you cross-build the project for multiple Scala versions
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    setNextVersion,
    commitNextVersion
  )
)
