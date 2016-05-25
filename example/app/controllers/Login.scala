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

class Login @Inject() (implicit val client: WSClient) extends Controller with AuthActions {

  import ExampleConfiguration.googleAuthConfig

  val ANTI_FORGERY_KEY = "antiForgeryToken"

  def login = Action { request =>
    val error = request.flash.get("error")
    Ok(views.html.login(error))
  }

  /*
  Redirect to Google with anti forgery token (that we keep in session storage - note that flashing is NOT secure)
   */
  def loginAction = Action.async { implicit request =>
    val antiForgeryToken = GoogleAuth.generateAntiForgeryToken()
    GoogleAuth.redirectToGoogle(googleAuthConfig, antiForgeryToken).map {
      _.withSession { request.session + (ANTI_FORGERY_KEY -> antiForgeryToken) }
    }
  }


  /*
  User comes back from Google.
  We must ensure we have the anti forgery token from the loginAction call and pass this into a verification call which
  will return a Future[UserIdentity] if the authentication is successful. If unsuccessful then the Future will fail.

   */
  def oauth2Callback = Action.async { implicit request =>
    val session = request.session
    session.get(ANTI_FORGERY_KEY) match {
      case None =>
        Future.successful(Redirect(routes.Login.login()).flashing("error" -> "Anti forgery token missing in session"))
      case Some(token) =>
        GoogleAuth.validatedUserIdentity(googleAuthConfig, token).map { identity =>
          // We store the URL a user was trying to get to in the LOGIN_ORIGIN_KEY in AuthAction
          // Redirect a user back there now if it exists
          val redirect = session.get(LOGIN_ORIGIN_KEY) match {
            case Some(url) => Redirect(url)
            case None => Redirect(routes.Application.index())
          }
          // Store the JSON representation of the identity in the session - this is checked by AuthAction later
          redirect.withSession {
            session + (UserIdentity.KEY -> Json.toJson(identity).toString) - ANTI_FORGERY_KEY - LOGIN_ORIGIN_KEY
          }
        } recover {
          case t =>
            // you might want to record login failures here - we just redirect to the login page
            Redirect(routes.Login.login())
              .withSession(session - ANTI_FORGERY_KEY)
              .flashing("error" -> s"Login failure: ${t.toString}")
        }
    }
  }

  def logout = Action { implicit request =>
    Redirect(routes.Application.index()).withNewSession
  }

  // TODO update example application
  def oauth2Callback2 = Action.async { implicit request =>
    val requiredGoogleGroups = Set("example")
    for {
      identity <- GoogleAuth.checkIdentity(googleAuthConf, failureUrl)
      _ <- GoogleAuth.enforceGoogleGroups(identity, requiredGoogleGroups, groupChecker, googleAuthConf, failureUrl)
    } yield {
      // happy days
      GoogleAuth.setupSessionWhenSuccessful(identity, googleAuthConf, defaultRedirectUrl)
    }
    ???
  }

}
