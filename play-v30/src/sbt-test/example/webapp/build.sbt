name := "play-googleauth-example"

scalaVersion := "2.13.18"

/*
 * To test whether any of these entries are redundant:
 * 1. Comment it out
 * 2. Run `sbt Runtime/dependencyList`
 * 3. If no earlier version appears in the dependency list, the entry can be removed.
 */
val safeTransitiveDependencies = Seq(
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.22.4",
)

libraryDependencies ++= Seq(
  "com.gu.play-googleauth" %% "play-v30" % version.value,
  ws
) ++ safeTransitiveDependencies

lazy val root = (project in file(".")).enablePlugins(PlayScala)
