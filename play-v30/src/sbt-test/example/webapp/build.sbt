name := "play-googleauth-example"

scalaVersion := "2.13.18"

libraryDependencies ++= Seq(
  "com.gu.play-googleauth" %% "play-v30" % version.value,
  ws
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

// Keep Jackson aligned with the version Play's Scala module requires
dependencyOverrides ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.3",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.14.3",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.14.3"
)
