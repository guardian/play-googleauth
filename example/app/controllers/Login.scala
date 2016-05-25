package controllers

import javax.inject.Inject
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import scala.concurrent.Future
import com.gu.googleauth._
import org.joda.time.Duration

trait AuthActions extends Actions with Filters {
  val loginTarget: Call = routes.Login.loginAction()
  val authConfig = ExampleConfiguration.googleAuthConfig
  lazy val groupChecker = new GoogleGroupChecker(???) // Can't share these credentials!
}

object ExampleConfiguration {
  val googleAuthConfig =
    GoogleAuthConfig(
      "863070799113-9oefm3nnjf0hu4g9k3k1ue3fopfvrtpg.apps.googleusercontent.com",  // The client ID from the dev console
      "5uLmlI8afy5vufKFWXWS2GPw",                  // The client secret from the dev console
      "http://localhost:9000/oauth2callback",      // The redirect URL Google send users back to (must be the same as
                                                   //    that configured in the developer console)
      Some("guardian.co.uk"),                      // Google App domain to restrict login
      Some(Duration.standardHours(1)),             // Force the user to re-enter their credentials if they haven't done
                                                   //    so in the last hour (this is stupid unless you are testing :)
      true                                         // Re-authenticate (without prompting) with google when session expires
    )

}

class Login @Inject() (implicit override val wsClient: WSClient) extends Controller with AuthActions {

  import ExampleConfiguration.googleAuthConfig

  val ANTI_FORGERY_KEY = "antiForgeryToken"
  val requiredGoogleGroups = Set("example")

  override val defaultRedirectTarget = routes.Login.login()
  override val failureRedirectTarget: Call = routes.Login.login()

  def login = Action { request =>
    val error = request.flash.get("error")
    Ok(views.html.login(error))
  }

  /*
  Redirect to Google with anti forgery token (that we keep in session storage - note that flashing is NOT secure)
   */
  def loginAction = Action.async { implicit request =>
    startGoogleLogin()
  }

  /*
  Looks up user's identity via Google and enforces required Google groups
   */
  def oauth2Callback = Action.async { implicit request =>
    (for {
      identity <- checkIdentity()
      _ <- enforceGoogleGroups(identity, requiredGoogleGroups, groupChecker)  // if Google group membership is required
    } yield {
      setupSessionWhenSuccessful(identity)
    }).merge
  }

  def logout = Action { implicit request =>
    Redirect(routes.Application.index()).withNewSession
  }
}
