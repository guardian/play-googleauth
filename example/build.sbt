name := "play-googleauth-example"

version := "0.1.5"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  "com.gu" %% "play-googleauth" % version.value,
  ws
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)