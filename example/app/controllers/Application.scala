package controllers

import com.gu.googleauth
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc.{Action, Controller}

import scala.concurrent.ExecutionContext.Implicits.global

object Application extends Controller with AuthActions {
  type GoogleAuthRequest[A] = AuthenticatedRequest[A, googleauth.UserIdentity]

  def index = Action { request => Ok(views.html.index(request)) }

  def authenticated = AuthAction { request => Ok(views.html.authenticated(request)) }

  def authenticatedAndInGroup = (AuthAction andThen requireGroup[GoogleAuthRequest](includedGroups = Set("membership.dev@guardian.co.uk"))) {
    request => Ok(views.html.authenticated(request))
  }
}