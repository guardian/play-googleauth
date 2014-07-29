package controllers

import play.api.mvc.{Action, Controller}

object Application extends Controller with AuthActions {
  def index = Action { request => Ok(views.html.index(request)) }
  def authenticated = AuthAction { request => Ok(views.html.authenticated(request)) }
}