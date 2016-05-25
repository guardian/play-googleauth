package com.gu.googleauth

import cats.data.{Xor, XorT}
import cats.std.future._
import cats.syntax.applicativeError._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Results.Redirect
import play.api.mvc.{Call, RequestHeader, Result, Session}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps


object PlayGoogleAuthHelpers {

  def checkIdentity(
    googleAuthConfig: GoogleAuthConfig,
    failureRedirectTarget: Call
  )(implicit request: RequestHeader): XorT[Future, Result, UserIdentity] = {
    GoogleAuth.validatedUserIdentity(googleAuthConfig, googleAuthConfig.antiForgeryKey).attemptT
      .leftMap { t =>
        Logger.warn("Login failure", t)
        redirectWithError(failureRedirectTarget, t.getMessage, googleAuthConfig.antiForgeryKey, request.session)
      }
  }

  def enforceGoogleGroups(
    userIdentity: UserIdentity,
    requiredGoogleGroups: Set[String],
    googleGroupChecker: GoogleGroupChecker,
    googleAuthConfig: GoogleAuthConfig,
    failureRedirectTarget: Call
  )(implicit request: RequestHeader): XorT[Future, Result, Unit] = {
    googleGroupChecker.retrieveGroupsFor(userIdentity.email).attemptT
      .leftMap { t =>
        Logger.warn("Login failure, Could not look up user's Google groups", t)
        redirectWithError(failureRedirectTarget, "Unable to look up user's 2FA status", googleAuthConfig.antiForgeryKey, request.session)
      }
      .map { userGroups =>
        if (checkGoogleGroups(userGroups, requiredGoogleGroups)) {
          Xor.right(userGroups)
        } else {
          Logger.info("Login failure, user not in 2FA group")
          Xor.left(redirectWithError(failureRedirectTarget, "You must be in the 2-factor auth Google group", googleAuthConfig.antiForgeryKey, request.session))
        }
      }
  }

  private[googleauth] def checkGoogleGroups(userGroups: Set[String], requiredGroups: Set[String]): Boolean = {
    if (userGroups.intersect(requiredGroups) == requiredGroups) true
    else false
  }

  private def redirectWithError(target: Call, message: String, antiForgeryKey: String, session: Session): Result = {
    Redirect(target)
      .withSession(session - antiForgeryKey)
      .flashing("error" -> s"Login failure. $message")
  }

  def setupSessionWhenSuccessful(
    userIdentity: UserIdentity,
    googleAuthConfig: GoogleAuthConfig,
    defaultRedirectUrl: Call
  )(implicit request: RequestHeader): Result = {
    val redirect = request.session.get(GoogleAuthFilters.LOGIN_ORIGIN_KEY) match {
      case Some(url) => Redirect(url)
      case None => Redirect(defaultRedirectUrl)
    }
    // Store the JSON representation of the identity in the session - this is checked by AuthAction later
    redirect.withSession {
      request.session + (UserIdentity.KEY -> Json.toJson(userIdentity).toString) - googleAuthConfig.antiForgeryKey - GoogleAuthFilters.LOGIN_ORIGIN_KEY
    }
  }
}
