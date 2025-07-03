package controllers

import com.gu.googleauth.{GoogleAuthConfig, GoogleGroupChecker, LoginSupport}
import com.gu.googleauth.Actions.GroupCheckConfig
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext


class Login(requiredGoogleGroups: Set[String], val authConfig: GoogleAuthConfig, googleGroupChecker: GoogleGroupChecker,
  override val wsClient: WSClient, val controllerComponents: ControllerComponents)
  (implicit executionContext: ExecutionContext)
  extends LoginSupport with BaseController {
  /**
   * Shows UI for login button and logout error feedback
   */
  def login = Action { request =>
    val error = request.flash.get("error")
    Ok(views.html.login(error))
  }

  /*
   * Redirect to Google with a signed anti-forgery token in the OAuth 'state'
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
//     processOauth2Callback()  // without Google group membership checks
    processOauth2Callback(GroupCheckConfig(requiredGroups = Some(requiredGoogleGroups)), googleGroupChecker)  // with optional Google group checks
  }

  def logout = Action { implicit request =>
    Redirect(routes.Application.index).withNewSession
  }

  override val failureRedirectTarget: Call = routes.Login.login
  override val defaultRedirectTarget: Call = routes.Application.authenticated
}
