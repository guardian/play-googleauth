name := "play-googleauth-example"

version := "0.4.1-SNAPSHOT"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.gu" %% "play-googleauth" % version.value,
  ws
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)