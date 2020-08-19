import sbt._

/**
  * This dependencies file is a pattern taken from Rule #2 of 'Effective SBT' by Josh Suereth:
  * https://docs.google.com/presentation/d/15gmGLiD4Eiyx8e5Kg2eXge5wD80DF6lLG8hSqc6ohmo/edit?usp=sharing
  *
  * It's useful on multi-module projects, but here I'm really using it to give myself more room to document
  * the trickier dependencies.
  */
object Dependencies {

  object PlayVersionSpecific {
    case class LibVersions(play: String, mockWs: String) {
      val playLibs: Seq[ModuleID] = Seq(
        "com.typesafe.play" %% "play" % play,
        "com.typesafe.play" %% "play-ws" % play,
        "com.typesafe.play" %% "play-test" % play % Test,
        "com.typesafe.play" %% "play-ahc-ws" % play % Test,
        "de.leanovate.play-mockws" %% "play-mockws" % mockWs % Test
      )
    }

    val libVersions = Map(
      "26" -> LibVersions(play = "2.6.25", mockWs = "2.6.6"),
      "27" -> LibVersions(play = "2.7.5", mockWs = "2.7.1"),
      "28" -> LibVersions(play = "2.8.2", mockWs = "2.8.0")
    )
  }

  val commonsCodec = "commons-codec" % "commons-codec" % "1.9"

  /** The google-api-services-admin-directory artifact has a transitive dependency on com.google.guava:guava-jdk5 - a
    * nasty artifact that clashes with the regular com.google.guava:guava artifact, providing two versions of the same
    * classes on your class path! To prevent problems, we specifically exclude this evil artifact, and ensure we have
    * regular guava available.
    *
    * @see https://github.com/guardian/subscriptions-frontend/pull/363#issuecomment-186190081
    */
  val googleDirectoryAPI = Seq(
    "com.google.apis" % "google-api-services-admin-directory" % "directory_v1-rev53-1.20.0" exclude("com.google.guava", "guava-jdk5"),
    "com.google.guava" % "guava" % "25.0-jre"
  )

}
