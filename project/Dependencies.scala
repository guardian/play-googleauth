import sbt._

/**
  * This dependencies file is a pattern taken from Rule #2 of 'Effective SBT' by Josh Suereth:
  * https://docs.google.com/presentation/d/15gmGLiD4Eiyx8e5Kg2eXge5wD80DF6lLG8hSqc6ohmo/edit?usp=sharing
  *
  * It's useful on multi-module projects, but here I'm really using it to give myself more room to document
  * the trickier dependencies.
  */
object Dependencies {

  private def exactPlayVersions(majorMinorVersion: String): String =
    majorMinorVersion match {
      case "27" => "2.7.3"
      case "28" => "2.8.1"
    }

  private def mockWsVersion(majorMinorVersion: String): String =
    majorMinorVersion match {
      case "27" => "2.7.1"
      case "28" => "2.8.0"
    }

  def playLibs(majorMinorVersion: String): Seq[ModuleID] = {
    val playVersion = exactPlayVersions(majorMinorVersion)

    val play = "com.typesafe.play" %% "play" % playVersion % "provided"
    val playWS = "com.typesafe.play" %% "play-ws" % playVersion % "provided"
    val playTest = "com.typesafe.play" %% "play-test" % playVersion % "test"

    // mockWs depends on some play-ahc-ws classes, so include them for tests
    val playAhcWs = "com.typesafe.play" %% "play-ahc-ws" % playVersion % Test
    val mockWs = "de.leanovate.play-mockws" %% "play-mockws" % mockWsVersion(majorMinorVersion) % Test
    Seq(play, playWS, playTest, playAhcWs, mockWs)
  }

  val commonsCodec = "commons-codec" % "commons-codec" % "1.14"

  /** The google-api-services-admin-directory artifact has a transitive dependency on com.google.guava:guava-jdk5 - a
    * nasty artifact that clashes with the regular com.google.guava:guava artifact, providing two versions of the same
    * classes on your class path! To prevent problems, we specifically exclude this evil artifact, and ensure we have
    * regular guava available.
    *
    * @see https://github.com/guardian/subscriptions-frontend/pull/363#issuecomment-186190081
    */
  val googleDirectoryAPI = Seq(
    "com.google.apis" % "google-api-services-admin-directory" % "directory_v1-rev20191003-1.30.8" exclude("com.google.guava", "guava-jdk5"),
    "com.google.api-client" % "google-api-client" % "1.31.1", // https://app.snyk.io/vuln/SNYK-JAVA-COMGOOGLEOAUTHCLIENT-575276
    "com.google.auth" % "google-auth-library-oauth2-http" % "0.22.0",
    "com.google.guava" % "guava" % "30.0-jre"
  )

}
