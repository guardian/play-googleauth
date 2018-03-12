package com.gu.googleauth

import java.time.Duration.ofSeconds
import java.time.ZoneOffset.UTC
import java.time._

import com.gu.play.secretrotation.DualSecretTransition.{InitialSecret, TransitioningSecret}
import io.jsonwebtoken.{ExpiredJwtException, SignatureException, UnsupportedJwtException}
import io.jsonwebtoken.SignatureAlgorithm.{HS256, HS384}
import org.scalatest.{FlatSpec, Matchers, TryValues}
import org.threeten.extra.Interval
import play.api.http.SecretConfiguration
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest

class AntiForgeryCheckerTest extends FlatSpec with Matchers with TryValues {

  val ExampleSessionId = OAuthStateSecurityConfig.generateSessionId()

  val antiForgery = OAuthStateSecurityConfig(InitialSecret(SecretConfiguration("reallySecret")), HS256)

  "Anti Forgery" should "fail if token is signed with other algorithm, even if it has the same secret" in {
    val badAlgorithmAntiForgery = antiForgery.copy(signatureAlgorithm = HS384)

      antiForgery.extractOAuthStateFrom(mockRequest(badAlgorithmAntiForgery.generateToken(ExampleSessionId), ExampleSessionId))
        .failure.exception should have message "the anti forgery token is not signed with HS256"
  }

  it should "fail if the Play session id is different to the token id" in {
    val otherSessionId = OAuthStateSecurityConfig.generateSessionId()
    val badSessionAntiForgery = antiForgery.generateToken(otherSessionId)

    antiForgery.extractOAuthStateFrom(mockRequest(badSessionAntiForgery, ExampleSessionId))
      .failure.exception should have message "the session ID found in the anti forgery token does not match the Play session ID"
  }

  it should "not allow the 'None' algorithm" in {
    val tokenSignedWithNoneAlgorithm = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJuYmYiOjE1MTUxNTE3ODUsImV4cCI6MjA1MTYwOTI5MDAwMH0."

    antiForgery.extractOAuthStateFrom(mockRequest(tokenSignedWithNoneAlgorithm, ExampleSessionId))
      .failure.exception shouldBe a [UnsupportedJwtException]
  }

  it should "not accept an expired token" in {
    val clockOneHourBehindCurrentTime = Clock.offset(Clock.systemUTC(), Duration.ofHours(1).negated())
    val expiredToken = antiForgery.generateToken(ExampleSessionId)(clockOneHourBehindCurrentTime)

    antiForgery.extractOAuthStateFrom(mockRequest(expiredToken, ExampleSessionId))
      .failure.exception shouldBe a [ExpiredJwtException]
  }

  it should "accept a valid token" in {
    val validToken = antiForgery.generateToken(ExampleSessionId)

    antiForgery.extractOAuthStateFrom(mockRequest(validToken, ExampleSessionId)).isSuccess shouldBe true
  }

  it should "not accept a token missing a character" in {
    val tokenMissingCharacter = antiForgery.generateToken(ExampleSessionId).dropRight(1)

      antiForgery.extractOAuthStateFrom(mockRequest(tokenMissingCharacter, ExampleSessionId))
      .failure.exception shouldBe a [SignatureException]
  }

  it should "accept a token signed with any of the accepted secrets" in {
    val overlapInterval = Interval.of(Instant.now().minusSeconds(50), ofSeconds(100))
    val checkerAcceptingMultipleSecrets = AntiForgeryChecker(
      TransitioningSecret(SecretConfiguration("alpha"),SecretConfiguration("beta"),
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

  def mockRequest(state: String, sessionId: String): RequestHeader = {
    FakeRequest("GET", path = s"?state=$state").withSession(antiForgery.sessionIdKeyName -> sessionId)
  }

}
