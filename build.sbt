import ReleaseTransformations.*
import Dependencies.*

name := "play-googleauth"

ThisBuild / scalaVersion := "2.13.11"

val sonatypeReleaseSettings = Seq(
  organization := "com.gu.play-googleauth",

  description := "Simple Google authentication module for Play 2 & 3",

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

def projectWithPlayVersion(playVersion: PlayVersion) =
  Project(playVersion.projectId, file(playVersion.projectId)).settings(
    crossScalaVersions := Seq(scalaVersion.value) ++ (if (playVersion.supportsScala3) Seq("3.3.1") else Seq.empty),
    scalacOptions ++= Seq("-feature", "-deprecation"),
    Compile / unmanagedSourceDirectories += baseDirectory.value / playVersion.pekkoOrAkkaSrcFolder,

    libraryDependencies ++= Seq(
      "com.gu.play-secret-rotation" %% "core" % "0.40",
      "org.typelevel" %% "cats-core" % "2.9.0",
      commonsCodec,
      "org.scalatest" %% "scalatest" % "3.2.16" % Test,
      "software.amazon.awssdk" % "ssm" % "2.21.7" % Test
    ) ++ googleDirectoryAPI ++ playVersion.playLibs,

    sonatypeReleaseSettings
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
  publishArtifact := false,
  publish / skip := true,

  sonatypeReleaseSettings
)
