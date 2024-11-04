import ReleaseTransformations.*
import Dependencies.*
import sbtversionpolicy.withsbtrelease.ReleaseVersion.fromAggregatedAssessedCompatibilityWithLatestRelease

name := "play-googleauth"

ThisBuild / scalaVersion := "2.13.15"
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("releases") // libraries that haven't yet synced to maven central

val artifactPomMetadataSettings = Seq(
  organization := "com.gu.play-googleauth",
  licenses := Seq(License.Apache2),
  description := "Simple Google authentication module for the Play web framework"
)

def projectWithPlayVersion(playVersion: PlayVersion) =
  Project(playVersion.projectId, file(playVersion.projectId)).settings(
    crossScalaVersions := Seq(scalaVersion.value, "3.3.4"),
    scalacOptions ++= Seq("-feature", "-deprecation", "-release","11"),
    Compile / unmanagedSourceDirectories += baseDirectory.value / playVersion.pekkoOrAkkaSrcFolder,

    libraryDependencies ++= Seq(
      "com.gu.play-secret-rotation" %% "core" % "11.3.6",
      "org.typelevel" %% "cats-core" % "2.12.0",
      commonsCodec,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "software.amazon.awssdk" % "ssm" % "2.28.29" % Test
    ) ++ googleDirectoryAPI ++ playVersion.playLibs,

    artifactPomMetadataSettings
  )

lazy val `play-v29` = projectWithPlayVersion(PlayVersion.V29)
lazy val `play-v30` = projectWithPlayVersion(PlayVersion.V30)

lazy val `play-googleauth-root` = (project in file(".")).aggregate(
  `play-v29`,
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
