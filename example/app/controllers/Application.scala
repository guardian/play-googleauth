package controllers

import javax.inject.Inject

import auth.AuthActions
import com.gu.googleauth
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc.{Action, Controller}

import scala.concurrent.ExecutionContext.Implicits.global


class Application @Inject() (override val wsClient: WSClient, override val conf: Configuration) extends AuthActions with Controller {
  type GoogleAuthRequest[A] = AuthenticatedRequest[A, googleauth.UserIdentity]

  def index = Action { request => Ok(views.html.index(request)) }

  def authenticated = AuthAction { request => Ok(views.html.authenticated(request)) }

  // checks Google group membership on every request
  def authenticatedAndInGroup = (AuthAction andThen requireGroup[GoogleAuthRequest](includedGroups = requiredGoogleGroups)) {
    request => Ok(views.html.authenticated(request))
  }
}
