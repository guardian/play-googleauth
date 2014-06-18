package controllers

import play.api.mvc.Controller

object Application extends Controller with AuthActions {
  def index = NonAuthAction { request => Ok(views.html.index(request)) }
  def authenticated = AuthAction { request => Ok(views.html.authenticated(request)) }
}