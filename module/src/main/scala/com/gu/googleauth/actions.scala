package com.gu.googleauth

import play.api.libs.json.{JsValue, Format, Json}
import play.api.mvc.Results._
import play.api.mvc._
import scala.concurrent.Future
import Function.const

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
  def fromRequestHeader(request: RequestHeader): Option[UserIdentity] = {
    request.session.get(KEY).flatMap(credentials => UserIdentity.fromJson(Json.parse(credentials)))
  }
  def fromRequest(request: Request[Any]): Option[UserIdentity] = fromRequestHeader(request)
}

class AuthenticatedRequest[A](val identity: Option[UserIdentity], request: Request[A]) extends WrappedRequest[A](request) {
  lazy val isAuthenticated = identity.isDefined
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
  def sendForAuth[A](request:Request[A]) =
    Future.successful(Redirect(loginTarget).withSession {
      request.session + (LOGIN_ORIGIN_KEY, request.uri)
    })


  object AuthActionBuilder {

    /**
     * Create an action builder which either:
     * a) forces the user to authenticate
     * b) executes the request block, passing in an AuthenticatedRequest with
     *    either a _valid_ user-identity or no user-identity at all
     *
     * @param forceAuth a predicate indicating whether a request's (optional, possibly expired) identity should cause
     *                  authentication to be forced.
     */
    def forceAuthIf(forceAuth: Option[UserIdentity] => Boolean) = new ActionBuilder[AuthenticatedRequest] {
      override def invokeBlock[A](request: Request[A],
                                  block: (AuthenticatedRequest[A]) => Future[Result]): Future[Result] = {
        val identityOpt = UserIdentity.fromRequest(request)

        if (forceAuth(identityOpt)) sendForAuth(request)
        else {
          block(new AuthenticatedRequest(identityOpt.filter(_.isValid), request))
        }
      }
    }

  }

  /**
   * This action should be used for any login screen.
   *
   * It is similar to NonAuthAction, but NEVER forces the user to authenticate
   * (ie it does not send users for re-authentication if their session has expired-
   * instead appears as if the user is logged out).
   */
  val LoginAuthAction = AuthActionBuilder.forceAuthIf(const(false))

  /**
   * This action can be used for pages where login is optional.
   *
   * The user is forced to authenticate only if they have invalid (ie expired) identity credentials.
   * Otherwise they can access the resource, and the AuthenticatedRequest will have their (valid) identity iff
   * they're logged in.
   */
  val NonAuthAction = AuthActionBuilder.forceAuthIf(id => id.exists(!_.isValid))

  /**
   * This action ensures that the user is authenticated and their token is valid.
   *
   * The user will be forced to authenticate if they do not have valid identity credentials.
   *
   * The AuthenticatedRequest will always have a valid identity.
   */
  val AuthAction = AuthActionBuilder.forceAuthIf(id => !id.exists(_.isValid))
}