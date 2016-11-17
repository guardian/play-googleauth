package auth

import com.gu.googleauth._
import controllers.routes
import play.api.Configuration

import scala.collection.JavaConverters._

/**
  * Configures your application for use with Play-GoogleAuth. `Actions` is required, `Filters` is required only
  * if you need to look up group membership on every page load (instead of just at login time).
  *
  * Your controllers can extend this trait to make `AuthAction` available (see `controllers/Application.scala`).
  */
trait AuthActions extends Actions with Filters {
  def conf: Configuration

  // Google configuration
  override val authConfig = GoogleAuthConfig(
    clientId     = conf.getString("your.clientId.config.path").get,
    clientSecret = conf.getString("your.clientSecret.config.path").get,
    redirectUrl  = conf.getString("your.redirectUrl.config.path").get,
    domain       = conf.getString("your.apps-domain.config.path").get
  )
  // your app's routing
  override val loginTarget = routes.Login.loginAction()
  override val defaultRedirectTarget = routes.Application.authenticated()
  override val failureRedirectTarget = routes.Login.login()

  // optionally, configuration to look up google group membership
  val requiredGoogleGroups =
    conf.getStringList("your.required-groups.config.path")
      .map(_.asScala.toSet)
      .getOrElse(Set.empty[String])
  // groupChecker only needed here because `Filters` is mixed-in (to provide the `requireGroup` filter example)
  override lazy val groupChecker = new GoogleGroupChecker(
    GoogleServiceAccount(???, ???, ???) // from your secret config (and/or via JSON service account credentials file)
  )
}
