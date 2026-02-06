import sbt._

/**
  * This dependencies file is a pattern taken from Rule #2 of 'Effective SBT' by Josh Suereth:
  * https://docs.google.com/presentation/d/15gmGLiD4Eiyx8e5Kg2eXge5wD80DF6lLG8hSqc6ohmo/edit?usp=sharing
  *
  * It's useful on multi-module projects, but here I'm really using it to give myself more room to document
  * the trickier dependencies.
  */
object Dependencies {

  case class PlayVersion(
    majorMinorVersion: String,
    groupId: String,
    exactPlayVersion: String
  ) {
    val projectId = s"play-v$majorMinorVersion"

    val playLibs: Seq[ModuleID] = {

      val play = groupId %% "play" % exactPlayVersion
      val playWS = groupId %% "play-ws" % exactPlayVersion
      val playTest = groupId %% "play-test" % exactPlayVersion % Test

      Seq(play, playWS, playTest, jackson)
    }
  }

  object PlayVersion {
    val V30 = PlayVersion("30", "org.playframework", "3.0.10")
  }

  val commonsCodec = "commons-codec" % "commons-codec" % "1.21.0"

  /** The google-api-services-admin-directory artifact has a transitive dependency on com.google.guava:guava-jdk5 - a
    * nasty artifact that clashes with the regular com.google.guava:guava artifact, providing two versions of the same
    * classes on your class path! To prevent problems, we specifically exclude this evil artifact, and ensure we have
    * regular guava available.
    *
    * @see https://github.com/guardian/subscriptions-frontend/pull/363#issuecomment-186190081
    */
  val googleDirectoryAPI = Seq(
    "com.google.apis" % "google-api-services-admin-directory" % "directory_v1-rev20260113-2.0.0",
    "com.google.api-client" % "google-api-client" % "2.8.1",
    "com.google.auth" % "google-auth-library-oauth2-http" % "1.42.1"
  ).map(_ exclude("com.google.guava", "guava-jdk5")) :+ "com.google.guava" % "guava" % "33.5.0-jre"

  // Play 3.0 is stuck on Jackson 2.14, which has 'high' vulnerabilities
  val jackson = "com.fasterxml.jackson.core" % "jackson-core" % "2.21.0"

}
