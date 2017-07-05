package controllers

import com.gu.googleauth
import com.gu.googleauth.AuthAction
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc._


class Application(authAction: AuthAction[AnyContent], override val controllerComponents: ControllerComponents) extends BaseController {
  type GoogleAuthRequest[A] = AuthenticatedRequest[A, googleauth.UserIdentity]

  def index = Action { request => Ok(views.html.index(request)) }

  def authenticated = authAction { request => Ok(views.html.authenticated(request)) }

}
