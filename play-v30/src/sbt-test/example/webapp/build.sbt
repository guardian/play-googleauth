name := "play-googleauth-example"

scalaVersion := "2.13.18"

libraryDependencies ++= Seq(
  "com.gu.play-googleauth" %% "play-v30" % version.value,
  ws,
  // must match the Jackson version pinned by play-googleauth, as Play 3.0 ships an older one
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.22.3"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)
