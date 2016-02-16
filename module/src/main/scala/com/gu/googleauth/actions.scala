package com.gu.googleauth

import play.api.libs.json.{JsValue, Format, Json}
import play.api.Logger
import play.api.mvc.Results._
import play.api.mvc.Security.{AuthenticatedBuilder, AuthenticatedRequest}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds


case class UserIdentity(sub: String, email: String, firstName: String, lastName: String, exp: Long, avatarUrl: Option[String]) {
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

trait UserIdentifier {
  /**
    * The configuration to use for these actions
    */
  def authConfig: GoogleAuthConfig

  /**
    * Helper method that deals with getting a user identity from a request and establishing validity
    */
  def userIdentity(request:RequestHeader) =
    UserIdentity.fromRequest(request).filter(_.isValid || !authConfig.enforceValidity)
}

trait Actions extends UserIdentifier {
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
  object AuthAction extends AuthenticatedBuilder(r => userIdentity(r), r => sendForAuth(r))
}

trait Filters extends UserIdentifier {
  def groupChecker: GoogleGroupChecker

  /**
    * This action ensures that the user is authenticated and has membership of *at least one* of the
    * specified groups. If you want to ensure membership of multiple groups, you can chain multiple
    * requireGroup() filters together.
    *
    * @param includedGroups if the user is a member of any one of these groups, they are allowed through
    */
  def requireGroup[R[_] <: RequestHeader](
    includedGroups: Set[String],
    notInValidGroup: R[_] => Result = (_: R[_])  => Forbidden
  )(implicit ec: ExecutionContext) = new ActionFilter[R] {

    protected def filter[A](request: R[A]) = userIdentity(request).fold[Future[Option[Result]]](Future.successful(Some(notInValidGroup(request)))) {
      user => for (usersGroups <- groupChecker.retrieveGroupsFor(user.email)) yield if (includedGroups.intersect(usersGroups).nonEmpty) None else {
        Logger.info(s"Excluding ${user.email} from '${request.path}' - not in accepted groups: $includedGroups")
        Some(notInValidGroup(request))
      }
    }

  }
}