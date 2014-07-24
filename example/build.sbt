name := "play-googleauth-example"

version := "0.1.3"

scalaVersion := "2.11.0"

libraryDependencies ++= Seq(
  "com.gu" %% "play-googleauth" % version.value,
  ws
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)