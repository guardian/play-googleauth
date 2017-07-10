package controllers

import com.gu.googleauth
import com.gu.googleauth.{AuthAction, Filters, GoogleAuthConfig, GoogleGroupChecker}
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc._

import scala.concurrent.ExecutionContext


class Application(authAction: AuthAction[AnyContent], requiredGoogleGroups: Set[String],
  val authConfig: GoogleAuthConfig, val groupChecker: GoogleGroupChecker, val controllerComponents: ControllerComponents)
  (implicit executionContext: ExecutionContext)
  extends BaseController with Filters {

  type GoogleAuthRequest[A] = AuthenticatedRequest[A, googleauth.UserIdentity]

  def index = Action { request => Ok(views.html.index(request)) }

  def authenticated = authAction { request => Ok(views.html.authenticated(request)) }

  // checks Google group membership on every request
  def authenticatedAndInGroup = (authAction andThen requireGroup[GoogleAuthRequest](includedGroups = requiredGoogleGroups)) {
    request => Ok(views.html.authenticated(request))
  }
}
