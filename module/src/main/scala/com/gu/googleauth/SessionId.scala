package com.gu.googleauth

import java.math.BigInteger
import java.security.SecureRandom

import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}

object SessionId {
  val KeyName = "play-googleauth-session-id"

  private val random = new SecureRandom()
  def generateSessionId() = new BigInteger(130, random).toString(32)

  def ensureUserHasSessionId(t: String => Future[Result])(implicit request: RequestHeader, ec: ExecutionContext):Future[Result] = {
    val sessionId = request.session.get(KeyName).getOrElse(generateSessionId())

    t(sessionId).map(_.addingToSession(KeyName -> sessionId))
  }
}
