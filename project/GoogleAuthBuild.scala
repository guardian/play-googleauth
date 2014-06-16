import sbt._
import Keys._
import play.Project._

object GoogleAuthBuild extends Build {

  val appName    = "play-googleauth"

  val playVersion = "2.2.0"

  lazy val baseSettings = Seq(
    version            := "0.0.1",
    scalaVersion       := "2.10.3",
    scalaBinaryVersion := "2.10",
    organization       := "com.gu",
    resolvers ++= Seq(
      "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      "Sonatype Snapshots"  at "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype Releases"  at "https://oss.sonatype.org/content/repositories/releases"
    )
  )

  lazy val appPublishMavenStyle = true
  lazy val appPublishArtifactInTest = false
  lazy val appPomIncludeRepository = { _: MavenRepository => false }
  lazy val appPublishTo = { (v: String) =>
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT")) 
      Some("snapshots" at nexus + "content/repositories/snapshots") 
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  }
  lazy val appPomExtra = {
        <url>https://github.com/guardian/play-googleauth</url>
        <licenses>
          <license>
            <name>Apache License, Version 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
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

  lazy val googleauth = Project("googleauth", base = file("module"))
    .settings(baseSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.play" %% "play" % playVersion % "provided",
        "commons-codec" % "commons-codec" % "1.9"
      ),
      name                    := appName,
      publishMavenStyle       := appPublishMavenStyle,
      publishArtifact in Test := appPublishArtifactInTest,
      pomIncludeRepository    := appPomIncludeRepository,
      publishTo               <<=(version)(appPublishTo),
      pomExtra                := appPomExtra
    )

  lazy val root = Project("root", base = file("."))
    .settings(baseSettings: _*)
    .settings(
      publishLocal := {},
      publish := {}
    ).aggregate(googleauth)

}
