package com.gu.googleauth

import cats.data.{Xor, XorT}
import cats.std.future._
import cats.syntax.applicativeError._
import play.api.libs.json.{Format, JsValue, Json}
import play.api.Logger
import play.api.libs.ws.WSClient
import play.api.mvc.Results._
import play.api.mvc.Security.{AuthenticatedBuilder, AuthenticatedRequest}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps


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
  def userIdentity(request: RequestHeader) =
    UserIdentity.fromRequest(request).filter(_.isValid || !authConfig.enforceValidity)
}

trait Actions extends UserIdentifier {
  implicit def wsClient: WSClient

  /**
   * The target that should be redirected to in order to carry out authentication
   */
  def loginTarget: Call

  /**
   * The target that should be redirected to if login fails
   */
  val failureRedirectTarget: Call

  /**
    * The target that should be redirected to if no redirect URL is provided (generally `home`)
    */
  val defaultRedirectTarget: Call

  /**
   * Helper method that deals with sending a client for authentication. Typically this should store the target URL and
   * redirect to the loginTarget. There shouldn't really be any need to override this.
   */
  def sendForAuth[A](request:RequestHeader) =
    Redirect(loginTarget).withSession {
      request.session + (GoogleAuthFilters.LOGIN_ORIGIN_KEY, request.uri)
    }

  /**
    * Redirects user to Google to start the login.
    */
  def startGoogleLogin()(implicit request: RequestHeader): Future[Result] = {
    val antiForgeryToken = GoogleAuth.generateAntiForgeryToken()
    GoogleAuth.redirectToGoogle(authConfig, antiForgeryToken).map {
      _.withSession { request.session + (authConfig.antiForgeryKey -> antiForgeryToken) }
    }
  }

  /**
   * Extracts user from Google response and validates it, redirecting to `failureRedirectTarget` if the check fails.
   */
  def checkIdentity()(implicit wsClient: WSClient, request: RequestHeader): XorT[Future, Result, UserIdentity] = {
    request.session.get(authConfig.antiForgeryKey) match {
      case Some(token) =>
        GoogleAuth.validatedUserIdentity(authConfig, token).attemptT.leftMap {
          case e: IllegalArgumentException =>
            Logger.warn("Login failure, anti-forgery token", e)
            redirectWithError(failureRedirectTarget, "Login failure, anti-forgery token did not match", authConfig.antiForgeryKey, request.session)
          case e: GoogleAuthException =>
            Logger.warn("Login failure, GoogleAuthException", e)
            redirectWithError(failureRedirectTarget, e.getMessage, authConfig.antiForgeryKey, request.session)
          case e: Throwable =>
            Logger.warn("Login failure", e)
            redirectWithError(failureRedirectTarget, e.getMessage, authConfig.antiForgeryKey, request.session)
        }
      case None =>
        Logger.warn("Login failure, anti-forgery token missing")
        XorT.left[Future, Result, UserIdentity] {
          Future.successful {
            redirectWithError(failureRedirectTarget, "Login failure, anti-forgery token missing", authConfig.antiForgeryKey, request.session)
          }
        }
    }
  }

  /**
   * Looks up user's Google Groups and ensures they belong to any that are required. Redirects to
    * `failureRedirectTarget` if the user is not a member of any required group.
   */
  def enforceGoogleGroups(userIdentity: UserIdentity, requiredGoogleGroups: Set[String], googleGroupChecker: GoogleGroupChecker)
                         (implicit request: RequestHeader): XorT[Future, Result, Unit] = {
    googleGroupChecker.retrieveGroupsFor(userIdentity.email).attemptT
      .leftMap { t =>
        Logger.warn("Login failure, Could not look up user's Google groups", t)
        redirectWithError(failureRedirectTarget, "Unable to look up user's 2FA status", authConfig.antiForgeryKey, request.session)
      }
      .map { userGroups =>
        if (Actions.checkGoogleGroups(userGroups, requiredGoogleGroups)) {
          Xor.right(())
        } else {
          Logger.info("Login failure, user not in 2FA group")
          Xor.left(redirectWithError(failureRedirectTarget, "You must be in the 2-factor auth Google group", authConfig.antiForgeryKey, request.session))
        }
      }
  }

  private def redirectWithError(target: Call, message: String, antiForgeryKey: String, session: Session): Result = {
    Redirect(target)
      .withSession(session - antiForgeryKey)
      .flashing("error" -> s"Login failure. $message")
  }

  /**
   * Redirects user with configured play-googleauth session.
   */
  def setupSessionWhenSuccessful(userIdentity: UserIdentity)(implicit request: RequestHeader): Result = {
    val redirect = request.session.get(GoogleAuthFilters.LOGIN_ORIGIN_KEY) match {
      case Some(url) => Redirect(url)
      case None => Redirect(defaultRedirectTarget)
    }
    // Store the JSON representation of the identity in the session - this is checked by AuthAction later
    redirect.withSession {
      request.session + (UserIdentity.KEY -> Json.toJson(userIdentity).toString) - authConfig.antiForgeryKey - GoogleAuthFilters.LOGIN_ORIGIN_KEY
    }
  }

  /**
   * This action ensures that the user is authenticated and their token is valid. Is a user is not logged in or their
   * token has expired then they will be authenticated.
   *
   * The AuthenticatedRequest will always have an identity.
   */
  object AuthAction extends AuthenticatedBuilder(r => userIdentity(r), r => sendForAuth(r))
}

object Actions {
  private[googleauth] def checkGoogleGroups(userGroups: Set[String], requiredGroups: Set[String]): Boolean = {
    if (userGroups.intersect(requiredGroups) == requiredGroups) true
    else false
  }
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
