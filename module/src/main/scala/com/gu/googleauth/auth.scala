package com.gu.googleauth

import java.math.BigInteger
import java.security.SecureRandom

import org.joda.time.Duration
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.Results.Redirect
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
 * The configuration class for Google authentication
 * @param clientId The ClientID from the developer dashboard
 * @param clientSecret The client secret from the developer dashboard
 * @param redirectUrl The URL to return to after authentication has completed
 * @param domain An optional domain to restrict login to (e.g. guardian.co.uk)
 * @param maxAuthAge An optional duration after which you want a user to be prompted for their password again
 * @param enforceValidity A boolean indicating whether you want a user to be re-authenticated when their session expires
 * @param prompt An optional space delimited, case sensitive list of ASCII string values that specifies whether the
 *               Authorization Server prompts the End-User for reauthentication and consent
 * @param antiForgeryKey A string that determines the session key used to store Google's anti forgery token
 */
case class GoogleAuthConfig private(
  clientId: String,
  clientSecret: String,
  redirectUrl: String,
  domain: Option[String],
  maxAuthAge: Option[Duration],
  enforceValidity: Boolean,
  prompt: Option[String],
  antiForgeryKey: String
)
object GoogleAuthConfig {
  private val defaultMaxAuthAge = None
  private val defaultEnforceValidity = true
  private val defaultPrompt = None
  private val defaultAntiForgeryKey = "antiForgeryToken"

  def apply(
    clientId: String,
    clientSecret: String,
    redirectUrl: String,
    domain: String,
    maxAuthAge: Option[Duration] = defaultMaxAuthAge,
    enforceValidity: Boolean = defaultEnforceValidity,
    prompt: Option[String] = defaultPrompt,
    antiForgeryKey: String = defaultAntiForgeryKey
  ): GoogleAuthConfig = GoogleAuthConfig(clientId, clientSecret, redirectUrl, Some(domain), maxAuthAge, enforceValidity, prompt, antiForgeryKey)

  /**
    * Creates a GoogleAuthConfig that does not restrict acceptable email domains.
    * This means any Google account can be used to gain access. If you mean to restrict
    * access to certain email domains use the `apply` method instead.
    */
  def withNoDomainRestriction(
    clientId: String,
    clientSecret: String,
    redirectUrl: String,
    maxAuthAge: Option[Duration] = defaultMaxAuthAge,
    enforceValidity: Boolean = defaultEnforceValidity,
    prompt: Option[String] = defaultPrompt,
    antiForgeryKey: String = defaultAntiForgeryKey
  ): GoogleAuthConfig = GoogleAuthConfig(clientId, clientSecret, redirectUrl, None, maxAuthAge, enforceValidity, prompt, antiForgeryKey)
}

class GoogleAuthException(val message: String, val throwable: Throwable = null) extends Exception(message, throwable)

object GoogleAuth {
  var discoveryDocumentHolder: Option[Future[DiscoveryDocument]] = None

  def discoveryDocument()(implicit context: ExecutionContext, ws: WSClient): Future[DiscoveryDocument] =
    if (discoveryDocumentHolder.isDefined) discoveryDocumentHolder.get
    else {
      val discoveryDocumentFuture = ws.url(DiscoveryDocument.url).get().map(r => DiscoveryDocument.fromJson(r.json))
      discoveryDocumentHolder = Some(discoveryDocumentFuture)
      discoveryDocumentFuture
    }

  val random = new SecureRandom()
  def generateAntiForgeryToken() = new BigInteger(130, random).toString(32)

  def googleResponse[T](r: WSResponse)(block: JsValue => T): T = {
    r.status match {
      case errorCode if errorCode >= 400 =>
        // try to get error if google sent us an error doc
        val error = (r.json \ "error").asOpt[Error]
        error.map { e =>
          throw new GoogleAuthException(s"Error when calling Google: ${e.message}")
        }.getOrElse {
          throw new GoogleAuthException(s"Unknown error when calling Google [status=$errorCode, body=${r.body}]")
        }
      case normal => block(r.json)
    }
  }

  def redirectToGoogle(config: GoogleAuthConfig, antiForgeryToken: String)
                      (implicit request: RequestHeader, context: ExecutionContext, ws: WSClient): Future[Result] = {
    val userIdentity = UserIdentity.fromRequest(request)
    val queryString: Map[String, Seq[String]] = Map(
      "client_id" -> Seq(config.clientId),
      "response_type" -> Seq("code"),
      "scope" -> Seq("openid email profile"),
      "redirect_uri" -> Seq(config.redirectUrl),
      "state" -> Seq(antiForgeryToken)) ++
      config.domain.map(domain => "hd" -> Seq(domain)) ++
      config.maxAuthAge.map(age => "max_auth_age" -> Seq(s"${age.getStandardSeconds}")) ++
      config.prompt.map(prompt => "prompt" -> Seq(prompt)) ++
      userIdentity.map(_.email).map("login_hint" -> Seq(_))

    discoveryDocument().map(dd => Redirect(s"${dd.authorization_endpoint}", queryString))
  }

  def validatedUserIdentity(config: GoogleAuthConfig, expectedAntiForgeryToken: String)
        (implicit request: RequestHeader, context: ExecutionContext, ws: WSClient): Future[UserIdentity] = {
    if (!request.queryString.getOrElse("state", Nil).contains(expectedAntiForgeryToken)) {
      throw new IllegalArgumentException("The anti forgery token did not match")
    } else {
      discoveryDocument().flatMap { dd =>
        val code = request.queryString("code")
        ws.url(dd.token_endpoint).post {
          Map(
            "code" -> code,
            "client_id" -> Seq(config.clientId),
            "client_secret" -> Seq(config.clientSecret),
            "redirect_uri" -> Seq(config.redirectUrl),
            "grant_type" -> Seq("authorization_code")
          )
        }.flatMap { response =>
          googleResponse(response) { json =>
              val token = Token.fromJson(json)
              val jwt = token.jwt
              config.domain foreach { domain =>
                if (!jwt.claims.email.split("@").lastOption.exists(_ == domain))
                  throw new GoogleAuthException("Configured Google domain does not match")
              }
              ws.url(dd.userinfo_endpoint)
                .withHeaders("Authorization" -> s"Bearer ${token.access_token}")
                .get().map { response =>
                  googleResponse(response) { json =>
                      val userInfo = UserInfo.fromJson(json)
                      UserIdentity(
                        jwt.claims.sub,
                        jwt.claims.email,
                        userInfo.given_name,
                        userInfo.family_name,
                        jwt.claims.exp,
                        userInfo.picture
                      )
                  }
              }
          }
        }
      }
    }
  }
}
