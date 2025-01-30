name := "play-googleauth-example"

scalaVersion := "2.13.16"

libraryDependencies ++= Seq(
  "com.gu.play-googleauth" %% "play-v30" % version.value,
  ws
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)
