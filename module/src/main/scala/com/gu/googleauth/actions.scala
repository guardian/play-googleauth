package com.gu.googleauth

import play.api.libs.json.{JsValue, Format, Json}
import play.api.mvc.Results._
import play.api.mvc._
import play.api.mvc.Security.{AuthenticatedBuilder, AuthenticatedRequest}

case class GoogleAuthResult(userIdentity: UserIdentity, userInfo: UserInfo)

case class UserIdentity(sub: String, email: String, firstName: String, lastName: String, exp: Long) {
  lazy val fullName = firstName + " " + lastName
  lazy val emailDomain = email.split("@").last
  lazy val asJson = Json.stringify(Json.toJson(this))
  lazy val isValid = System.currentTimeMillis < exp * 1000
}

object UserIdentity {
  implicit val userIdentityFormats: Format[UserIdentity] = Json.format[UserIdentity]
  val KEY = "identity"
  def fromJson(json: JsValue): Option[UserIdentity] = json.asOpt[UserIdentity]
  def fromRequest(request: RequestHeader): Option[UserIdentity] = {
    request.session.get(KEY).flatMap(credentials => UserIdentity.fromJson(Json.parse(credentials)))
  }
}

object AuthenticatedRequest {
  def apply[A](request: Request[A]) = {
    new AuthenticatedRequest(UserIdentity.fromRequest(request), request)
  }
}

trait Actions {
  /**
   * A Play session key that stores the target URL that was being accessed when redirected for authentication
   */
  val LOGIN_ORIGIN_KEY = "loginOriginUrl"

  /**
   * The target that should be redirected to in order to carry out authentication
   */
  def loginTarget: Call

  /**
   * Helper method that deals with sending a client for authentication. Typically this should store the target URL and
   * redirect to the loginTarget. There shouldn't really be any need to override this.
   */
  def sendForAuth[A](request:RequestHeader) =
    Redirect(loginTarget).withSession {
      request.session + (LOGIN_ORIGIN_KEY, request.uri)
    }

  /**
   * This action ensures that the user is authenticated and their token is valid. Is a user is not logged in or their
   * token has expired then they will be authenticated.
   *
   * The AuthenticatedRequest will always have an identity.
   */
  object AuthAction extends AuthenticatedBuilder(r => UserIdentity.fromRequest(r), r => sendForAuth(r))
}