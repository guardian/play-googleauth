import ReleaseTransformations._
import Dependencies._

name               := "play-googleauth"

val sonatypeReleaseSettings = Seq(
  sonatypeProfileName := "com.gu",

  organization := "com.gu.play-googleauth",

  description        := "Simple Google authentication module for Play 2",

  licenses := Seq("Apache V2" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),

  publishTo := sonatypePublishTo.value,

  pomExtra := {
    <url>https://github.com/guardian/play-googleauth</url>
      <developers>
        <developer>
          <id>sihil</id>
          <name>Simon Hildrew</name>
          <url>https://github.com/sihil</url>
        </developer>
      </developers>
  },

  scmInfo := Some(ScmInfo(
    url("https://github.com/guardian/play-googleauth"),
    "scm:git:git@github.com:guardian/play-googleauth.git"
  )),

  releasePublishArtifactsAction := PgpKeys.publishSigned.value,

  releaseProcess := Seq(
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    // For cross-build projects, use releaseStepCommand("+publishSigned")
    releaseStepCommandAndRemaining("publishSigned"),
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
)

def projectWithPlayVersion(majorMinorVersion: String) =
  Project(s"play-v$majorMinorVersion", file(s"play-v$majorMinorVersion")).settings(
    scalaVersion       := "2.12.9",

    scalacOptions ++= Seq("-feature", "-deprecation"),

    libraryDependencies ++= Seq(
      "com.gu.play-secret-rotation" %% "core" % "0.15",
      "org.typelevel" %% "cats-core" % "1.0.1",
      commonsCodec,
      "org.scalatest" %% "scalatest" % "3.0.3" % "test"
    ) ++ googleDirectoryAPI ++ playLibs(majorMinorVersion),
    
    sonatypeReleaseSettings
  )

lazy val `play-v26` = projectWithPlayVersion("26")
lazy val `play-v27` = projectWithPlayVersion("27")

lazy val `play-googleauth-root` = (project in file(".")).aggregate(
  `play-v26`,
  `play-v27`
).settings(
  publishArtifact := false,
  skip in publish := true,

  sonatypeReleaseSettings
)