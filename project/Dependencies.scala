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
    exactPlayVersion: String,
    supportsScala3: Boolean = false,
    usesPekko: Boolean = false
  ) {
    val projectId = s"play-v$majorMinorVersion"

    val playLibs: Seq[ModuleID] = {

      val play = groupId %% "play" % exactPlayVersion % Provided
      val playWS = groupId %% "play-ws" % exactPlayVersion % Provided
      val playTest = groupId %% "play-test" % exactPlayVersion % Test

      Seq(play, playWS, playTest)
    }

    val pekkoOrAkkaSrcFolder = s"src-${if (usesPekko) "pekko" else "akka"}"
  }

  object PlayVersion {
    val V27 = PlayVersion("27", "com.typesafe.play", "2.7.9")
    val V28 = PlayVersion("28", "com.typesafe.play", "2.8.20")
    val V29 = PlayVersion("29", "com.typesafe.play", "2.9.0", supportsScala3 = true)
    val V30 = PlayVersion("30", "org.playframework", "3.0.0", supportsScala3 = true, usesPekko = true)
  }

  val commonsCodec = "commons-codec" % "commons-codec" % "1.16.0"

  /** The google-api-services-admin-directory artifact has a transitive dependency on com.google.guava:guava-jdk5 - a
    * nasty artifact that clashes with the regular com.google.guava:guava artifact, providing two versions of the same
    * classes on your class path! To prevent problems, we specifically exclude this evil artifact, and ensure we have
    * regular guava available.
    *
    * @see https://github.com/guardian/subscriptions-frontend/pull/363#issuecomment-186190081
    */
  val googleDirectoryAPI = Seq(
    "com.google.apis" % "google-api-services-admin-directory" % "directory_v1-rev20231113-2.0.0",
    "com.google.api-client" % "google-api-client" % "2.2.0", // https://security.snyk.io/vuln/SNYK-JAVA-COMGOOGLEOAUTHCLIENT-2807808
    "com.google.auth" % "google-auth-library-oauth2-http" % "1.20.0"
  ).map(_ exclude("com.google.guava", "guava-jdk5")) :+ "com.google.guava" % "guava" % "32.1.1-jre"

}
