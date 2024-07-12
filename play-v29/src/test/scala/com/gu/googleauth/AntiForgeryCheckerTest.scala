package com.gu.googleauth

import com.gu.play.secretrotation.DualSecretTransition.{InitialSecret, TransitioningSecret}
import io.jsonwebtoken.SignatureAlgorithm.{HS256, HS384}
import io.jsonwebtoken.security.SignatureException
import io.jsonwebtoken.{ExpiredJwtException, UnsupportedJwtException}
import org.scalatest.TryValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.threeten.extra.Interval
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest

import java.time.Duration.ofSeconds
import java.time.ZoneOffset.UTC
import java.time._

class AntiForgeryCheckerTest extends AnyFlatSpec with Matchers with TryValues {

  val ExampleSessionId = AntiForgeryChecker.generateSessionId()

  def lengthySecret(prefix: String) = s"${prefix}_${"pad" * 256}"

  val antiForgery = AntiForgeryChecker(InitialSecret(lengthySecret("reallySecret")), HS256)

  "Anti Forgery" should "fail if token is signed with other algorithm, even if it has the same secret" in {
    val badAlgorithmAntiForgery = antiForgery.copy(signatureAlgorithm = HS384)

      antiForgery.verifyToken(mockRequest(badAlgorithmAntiForgery.generateToken(ExampleSessionId), ExampleSessionId))
        .failure.exception should have message "the anti forgery token is not signed with HS256"
  }

  it should "fail if the Play session id is different to the token id" in {
    val otherSessionId = AntiForgeryChecker.generateSessionId()
    val badSessionAntiForgery = antiForgery.generateToken(otherSessionId)

    antiForgery.verifyToken(mockRequest(badSessionAntiForgery, ExampleSessionId))
      .failure.exception should have message "the session ID found in the anti forgery token does not match the Play session ID"
  }

  it should "not allow the 'None' algorithm" in {
    val tokenSignedWithNoneAlgorithm = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJuYmYiOjE1MTUxNTE3ODUsImV4cCI6MjA1MTYwOTI5MDAwMH0."

    antiForgery.verifyToken(mockRequest(tokenSignedWithNoneAlgorithm, ExampleSessionId))
      .failure.exception shouldBe a [UnsupportedJwtException]
  }

  it should "not accept an expired token" in {
    val clockOneHourBehindCurrentTime = Clock.offset(Clock.systemUTC(), Duration.ofHours(1).negated())
    val expiredToken = antiForgery.generateToken(ExampleSessionId)(clockOneHourBehindCurrentTime)

    antiForgery.verifyToken(mockRequest(expiredToken, ExampleSessionId))
      .failure.exception shouldBe a [ExpiredJwtException]
  }

  it should "accept a valid token" in {
    val validToken = antiForgery.generateToken(ExampleSessionId)

    antiForgery.verifyToken(mockRequest(validToken, ExampleSessionId)).isSuccess shouldBe true
  }

  it should "not accept a token missing a character" in {
    val tokenMissingCharacter = antiForgery.generateToken(ExampleSessionId).dropRight(1)

      antiForgery.verifyToken(mockRequest(tokenMissingCharacter, ExampleSessionId))
      .failure.exception shouldBe a [SignatureException]
  }

  it should "accept a token signed with any of the accepted secrets" in {
    val overlapInterval = Interval.of(Instant.now().minusSeconds(50), ofSeconds(100))
    val checkerAcceptingMultipleSecrets = AntiForgeryChecker(
      TransitioningSecret(lengthySecret("alpha"),lengthySecret("beta"),
        overlapInterval), HS256)

    val tokenSignedWithOlderSecret =
      checkerAcceptingMultipleSecrets.generateToken(ExampleSessionId)(Clock.fixed(overlapInterval.getStart.minusSeconds(1), UTC))

    val tokenSignedWithNewerSecret =
      checkerAcceptingMultipleSecrets.generateToken(ExampleSessionId)(Clock.fixed(overlapInterval.getEnd.plusSeconds(1), UTC))

    checkerAcceptingMultipleSecrets.verifyToken(mockRequest(tokenSignedWithOlderSecret, ExampleSessionId)).isSuccess shouldBe true
    checkerAcceptingMultipleSecrets.verifyToken(mockRequest(tokenSignedWithNewerSecret, ExampleSessionId)).isSuccess shouldBe true

    checkerAcceptingMultipleSecrets.verifyToken(mockRequest(tokenSignedWithNewerSecret.dropRight(1), ExampleSessionId))
      .failure.exception shouldBe a [SignatureException]
  }

  def mockRequest(state: String, sessionId: String): RequestHeader =
    FakeRequest("GET", path = s"?state=$state").withSession(antiForgery.sessionIdKeyName -> sessionId)

}
