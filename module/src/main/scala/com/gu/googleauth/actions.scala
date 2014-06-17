package com.gu.googleauth

import play.api.libs.json.{JsValue, Format, Json}
import play.api.mvc.Results._
import play.api.mvc._
import scala.concurrent.Future

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
  val LOGIN_ORIGIN_KEY = "loginOriginUrl"
  def loginTarget: Call

  def sendForAuth[A](request:Request[A]) =
    Future.successful(Redirect(loginTarget).withSession {
      request.session + (LOGIN_ORIGIN_KEY, request.uri)
    })

  object NonAuthAction extends ActionBuilder[AuthenticatedRequest] {
    override protected def invokeBlock[A](request: Request[A],
                                          block: (AuthenticatedRequest[A]) => Future[SimpleResult]): Future[SimpleResult] = {
      UserIdentity.fromRequest(request) match {
        //case Some(identity) if !identity.isValid => sendForAuth(request)
        case otherIdentity => block(new AuthenticatedRequest(otherIdentity, request))
      }
    }
  }

  object AuthAction extends ActionBuilder[AuthenticatedRequest] {
    override protected def invokeBlock[A](request: Request[A],
                                          block: (AuthenticatedRequest[A]) => Future[SimpleResult]): Future[SimpleResult] = {
      UserIdentity.fromRequest(request) match {
        case Some(identity) if identity.isValid => block(new AuthenticatedRequest(Some(identity), request))
        case _ => sendForAuth(request)
      }
    }
  }
}