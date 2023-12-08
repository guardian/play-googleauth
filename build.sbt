import ReleaseTransformations.*
import Dependencies.*
import sbtversionpolicy.withsbtrelease.ReleaseVersion.fromAggregatedAssessedCompatibilityWithLatestRelease

name := "play-googleauth"

ThisBuild / scalaVersion := "2.13.12"

val artifactPomMetadataSettings = Seq(
  organization := "com.gu.play-googleauth",

  description := "Simple Google authentication module for Play 2 & 3",

  licenses := Seq("Apache V2" -> url("https://www.apache.org/licenses/LICENSE-2.0.html"))
)

def projectWithPlayVersion(playVersion: PlayVersion) =
  Project(playVersion.projectId, file(playVersion.projectId)).settings(
    crossScalaVersions := Seq(scalaVersion.value) ++ (if (playVersion.supportsScala3) Seq("3.3.1") else Seq.empty),
    scalacOptions ++= Seq("-feature", "-deprecation"),
    Compile / unmanagedSourceDirectories += baseDirectory.value / playVersion.pekkoOrAkkaSrcFolder,

    libraryDependencies ++= Seq(
      "com.gu.play-secret-rotation" %% "core" % "6.0.2",
      "org.typelevel" %% "cats-core" % "2.10.0",
      commonsCodec,
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
      "software.amazon.awssdk" % "ssm" % "2.21.35" % Test
    ) ++ googleDirectoryAPI ++ playVersion.playLibs,

    artifactPomMetadataSettings
  )

lazy val `play-v27` = projectWithPlayVersion(PlayVersion.V27)
lazy val `play-v28` = projectWithPlayVersion(PlayVersion.V28)
lazy val `play-v29` = projectWithPlayVersion(PlayVersion.V29)
lazy val `play-v30` = projectWithPlayVersion(PlayVersion.V30)

lazy val `play-googleauth-root` = (project in file(".")).aggregate(
  `play-v27`,
  `play-v28`,
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
