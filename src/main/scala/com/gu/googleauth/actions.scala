package com.gu.googleauth

import akka.http.scaladsl.model.Uri
import cats.data.EitherT
import cats.instances.future._
import cats.syntax.applicativeError._
import com.gu.googleauth.OAuthStateSecurityConfig.SessionIdJWTClaimPropertyName
import com.gu.googleauth.GoogleAuthFilters.LOGIN_ORIGIN_KEY
import com.gu.googleauth.SessionId.ensureUserHasSessionId
import io.jsonwebtoken.{ExpiredJwtException, Jwts}
import org.jose4j.jwa.AlgorithmConstraints
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType.WHITELIST
import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.libs.json.{Format, JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.Results._
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.{higherKinds, postfixOps}
import scala.util.Try
import AuthAction._


case class UserIdentity(sub: String, email: String, firstName: String, lastName: String, exp: Long, avatarUrl: Option[String]) {
  lazy val fullName = firstName + " " + lastName
  lazy val username = email.split("@").head
  lazy val emailDomain = email.split("@").last
  lazy val asJson = Json.stringify(toJson(this))
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

 // stored in the JWT claim - everything else is just anti-forgery verification



case class OAuthConclusion(user: UserIdentity, returnUrl: String) {
  def redirect(implicit req: RequestHeader) = Redirect(returnUrl).addingToSession(UserIdentity.KEY -> toJson(user).toString)

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

object AuthAction {
  type UserIdentityRequest[A] = AuthenticatedRequest[A, UserIdentity]

  implicit class RichCall(call: Call) {
    def withQueryParameter(keyValue: (String, String)): Call = {
      val callUri = Uri(call.url)
      call.copy(url = callUri.withQuery(keyValue +: callUri.query()).toString)
    }
  }
}

/**
  * This action ensures that the user is authenticated and their token is valid. Is a user is not logged in or their
  * token has expired then they will be authenticated.
  *
  * The AuthenticatedRequest will always have an identity.
  *
  * @param authConfig
  * @param loginTarget The target that should be redirected to in order to carry out authentication
  */
class AuthAction[A](val authConfig: GoogleAuthConfig, loginTarget: Call, bodyParser: BodyParser[A])(implicit val executionContext: ExecutionContext)
  extends ActionBuilder[AuthAction.UserIdentityRequest, A]
    with ActionRefiner[Request, AuthAction.UserIdentityRequest]
    with UserIdentifier {

  override protected def refine[A](request: Request[A]): Future[Either[Result, AuthAction.UserIdentityRequest[A]]] =
    Future.successful(
      userIdentity(request)
        .map(userID => new AuthenticatedRequest(userID, request))
        .toRight(sendForAuth(request)(executionContext))
    )

  /**
    * Helper method that deals with sending a client for authentication. Typically this should store the target URL and
    * redirect to the loginTarget. There shouldn't really be any need to override this.
    */
  def sendForAuth[A](request: RequestHeader)(implicit ec: ExecutionContext) = {
    val de: Destination.Encryption = ???

    Redirect(loginTarget.withQueryParameter(LOGIN_ORIGIN_KEY -> de.encrypt(request.uri)))
  }

  override def parser: BodyParser[A] = bodyParser

}

trait LoginSupport {
  implicit def wsClient: WSClient

  /**
    * The configuration to use for these actions
    */
  def authConfig: GoogleAuthConfig


  /**
    * The target that should be redirected to if login fails
    */
  val failureRedirectTarget: Call

  /**
    * The target that should be redirected to if no redirect URL is provided (generally `home`)
    */
  val defaultRedirectTarget: Call


  /**
    * Redirects user to Google to start the login.
    */
  def startGoogleLogin()(implicit req: RequestHeader, ec: ExecutionContext): Future[Result] = ensureUserHasSessionId {
    sessionId => GoogleAuth.redirectToGoogle(authConfig, sessionId)
  }

  /**
    * Extracts user from Google response and validates it, redirecting to `failureRedirectTarget` if the check fails.
    */
  def checkIdentity()(implicit request: RequestHeader, ec: ExecutionContext): EitherT[Future, Result, UserIdentity] = {
    def logWarn(desc:String, e: Throwable): Unit = {
      Logger.warn(s"${getClass.getSimpleName} : failed-oauth-callback : $desc : '${e.getMessage}'", e)
    }

    GoogleAuth.validatedUserIdentity(authConfig).attemptT.leftSemiflatMap {
      case expiredJwt: ExpiredJwtException =>
        logWarn("resend-user-with-expired-anti-forgery-token-to-google", expiredJwt)
        startGoogleLogin()
      case e: IllegalArgumentException =>
        logWarn("anti-forgery-token-invalid", e)
        Future.successful(redirectWithError(failureRedirectTarget, "The anti forgery token is not valid"))
      case e: GoogleAuthException =>
        logWarn("GoogleAuthException", e)
        Future.successful(redirectWithError(failureRedirectTarget, e.getMessage))
      case e: Throwable =>
        logWarn(e.getClass.getSimpleName, e)
        Future.successful(redirectWithError(failureRedirectTarget, e.getMessage))
    }
  }

  /**
    * Looks up user's Google Groups and ensures they belong to any that are required. Redirects to
    * `failureRedirectTarget` if the user is not a member of any required group.
    */
  def enforceGoogleGroups(userIdentity: UserIdentity, requiredGoogleGroups: Set[String], googleGroupChecker: GoogleGroupChecker, errorMessage: String = "Login failure. You do not belong to the required Google groups")
                         (implicit request: RequestHeader, ec: ExecutionContext): EitherT[Future, Result, Unit] = {
    googleGroupChecker.retrieveGroupsFor(userIdentity.email).attemptT
      .leftMap { t =>
        Logger.warn("Login failure, Could not look up user's Google groups", t)
        redirectWithError(failureRedirectTarget, "Login failure. Unable to look up Google Group membership")
      }
      .subflatMap { userGroups =>
        if (Actions.checkGoogleGroups(userGroups, requiredGoogleGroups)) {
          Right(())
        } else {
          Logger.info("Login failure, user not in required Google groups")
          Left(redirectWithError(failureRedirectTarget, errorMessage))
        }
      }
  }

  /**
    * Handle the OAuth2 callback, which logs the user in and redirects them appropriately.
    */
  def processOauth2Callback()(implicit request: RequestHeader, ec: ExecutionContext): Future[Result] = {
    val oauthStateEncoding: OAuthState.Encoding = ???
    val destinationEncryption: Destination.Encryption = ???

    for {
      oAuthState <- Future.fromTry(oauthStateEncoding.extractOAuthStateFrom(request.getQueryString("state").get))
      _ <- oAuthState.checkSessionIdMatches(request.session)
      userIdentity <- GoogleAuth.validatedUserIdentity(authConfig)
    } yield OAuthConclusion(
      user = userIdentity,
      returnUrl = destinationEncryption.decrypt(oAuthState.encryptedReturnUrl)
    ).redirect
  }

  /**
    * Handle the OAuth2 callback, which logs the user in and redirects them appropriately.
    *
    * Also ensures the user belongs to the (provided) required Google Groups.
    */
  def processOauth2Callback(requiredGoogleGroups: Set[String], groupChecker: GoogleGroupChecker)
    (implicit request: RequestHeader, ec: ExecutionContext): Future[Result] = {
    (for {
      identity <- checkIdentity()
      _ <- enforceGoogleGroups(identity, requiredGoogleGroups, groupChecker)
    } yield {
      setupSessionWhenSuccessful(identity)
    }).merge
  }

  def redirectWithError(target: Call, message: String): Result =
    Redirect(target).flashing("error" -> s"Login failure. $message")

  /**
    * Redirects user with configured play-googleauth session.
    */
  def setupSessionWhenSuccessful(userIdentity: UserIdentity)(implicit request: RequestHeader): Result = {
    val redirect = request.session.get(LOGIN_ORIGIN_KEY) match {
      case Some(url) => Redirect(url)
      case None => Redirect(defaultRedirectTarget)
    }
    // Store the JSON representation of the identity in the session - this is checked by AuthAction later
    redirect.addingToSession(UserIdentity.KEY -> toJson(userIdentity).toString)
  }
}

object Actions {
  private[googleauth] def checkGoogleGroups(userGroups: Set[String], requiredGroups: Set[String]): Boolean = {
    userGroups.intersect(requiredGroups) == requiredGroups
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

    override protected def executionContext: ExecutionContext = ec

    protected def filter[A](request: R[A]): Future[Option[Result]] =
      userIdentity(request: RequestHeader).fold[Future[Option[Result]]](Future.successful(Some(notInValidGroup(request)))) {
        user => for (usersGroups <- groupChecker.retrieveGroupsFor(user.email)) yield if (includedGroups.intersect(usersGroups).nonEmpty) None else {
          Logger.info(s"Excluding ${user.email} from '${request.path}' - not in accepted groups: $includedGroups")
          Some(notInValidGroup(request))
        }
      }
  }
}
