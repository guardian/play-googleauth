package com.gu.googleauth

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Clock
import java.util.{Base64, Date}

import com.gu.googleauth.Destination.Encryption._
import com.gu.googleauth.GoogleAuthFilters.LOGIN_ORIGIN_KEY
import com.gu.googleauth.OAuthStateSecurityConfig.SessionIdJWTClaimPropertyName
import io.jsonwebtoken.{Claims, Jws, Jwts, SignatureAlgorithm}
import org.jose4j.jwa.AlgorithmConstraints
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType.WHITELIST
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256
import org.jose4j.jwe.JsonWebEncryption
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers.A128KW
import org.jose4j.keys.AesKey
import play.api.mvc.Session

import scala.util.{Failure, Success, Try}


case class OAuthState(sessionId: String, encryptedReturnUrl: String) {
  def checkSessionIdMatches(session: Session):Try[Unit] =
    if (session(SessionId.KeyName).contains(sessionId)) Success(()) else
    Failure(throw new IllegalArgumentException(s"Session id does not match"))
}

object Destination {
  val KeyName = "destinationUrl"

  object Encryption {
    val KeyManagementAlgorithm = A128KW
    val ContentEncryptionAlgorithm = AES_128_CBC_HMAC_SHA_256
  }

  case class Encryption(secret: String) {

    val key = new AesKey(secret.getBytes(UTF_8))

    def encrypt(destinationUrl: String): String = {
      var jwe = new JsonWebEncryption
      jwe.setPayload(destinationUrl)
      jwe.setAlgorithmHeaderValue(KeyManagementAlgorithm)
      jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithm)
      jwe.setKey(key)
      jwe.getCompactSerialization
    }

    def decrypt(encryptedDestinationUrl: String): String = {
      val jwe = new JsonWebEncryption
      jwe.setAlgorithmConstraints(new AlgorithmConstraints(WHITELIST, KeyManagementAlgorithm))
      jwe.setContentEncryptionAlgorithmConstraints(new AlgorithmConstraints(WHITELIST, ContentEncryptionAlgorithm))
      jwe.setKey(key)
      jwe.setCompactSerialization(encryptedDestinationUrl)
      jwe.getPayload
    }
  }
}

object OAuthState {

  case class Encoding(secret: String, signatureAlgorithm: SignatureAlgorithm) {

    private val base64EncodedSecret: String =
      Base64.getEncoder.encodeToString(secret.getBytes(UTF_8))

    def checkChoiceOfSigningAlgorithm(claims: Jws[Claims]): Try[Unit] =
      if (claims.getHeader.getAlgorithm == signatureAlgorithm.getValue) Success(()) else
        Failure(throw new IllegalArgumentException(s"the anti forgery token is not signed with $signatureAlgorithm"))

    def extractOAuthStateFrom(state: String): Try[OAuthState] = for {
      jwtClaims <- Try(Jwts.parser().setSigningKey(base64EncodedSecret).parseClaimsJws(state))
      _ <- checkChoiceOfSigningAlgorithm(jwtClaims)
    } yield OAuthState(
      sessionId = jwtClaims.getBody.get(SessionIdJWTClaimPropertyName, classOf[String]),
      encryptedReturnUrl = jwtClaims.getBody.get(LOGIN_ORIGIN_KEY, classOf[String])
    )

    def stringify(oAuthState: OAuthState)(implicit clock: Clock = Clock.systemUTC) : String = Jwts.builder()
      .setExpiration(Date.from(clock.instant().plusSeconds(60)))
      .claim(SessionIdJWTClaimPropertyName, oAuthState.sessionId)
      .claim(LOGIN_ORIGIN_KEY, oAuthState.encryptedReturnUrl)
      .signWith(signatureAlgorithm, base64EncodedSecret)
      .compact()
  }
}
