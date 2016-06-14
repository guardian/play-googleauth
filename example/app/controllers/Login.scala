package controllers

import javax.inject.Inject

import auth.AuthActions
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc._


class Login @Inject() (override val wsClient: WSClient, override val conf: Configuration) extends AuthActions with Controller {
  /**
   * Shows UI for login button and logout error feedback
   */
  def login = Action { request =>
    val error = request.flash.get("error")
    Ok(views.html.login(error))
  }

  /*
   * Redirect to Google with anti forgery token (that we keep in session storage - note that flashing is NOT secure).
   */
  def loginAction = Action.async { implicit request =>
    startGoogleLogin()
  }

  /*
   * Looks up user's identity via Google and (optionally) enforces required Google groups at login time.
   *
   * To re-check Google group membership on every page request you can use the `requireGroup` filter
   * (see `Application.scala`).
   */
  def oauth2Callback = Action.async { implicit request =>
    // processOauth2Callback()  // without Google group membership checks
    processOauth2Callback(requiredGoogleGroups, groupChecker)  // with optional Google group checks
  }

  def logout = Action { implicit request =>
    Redirect(routes.Application.index()).withNewSession
  }
}
