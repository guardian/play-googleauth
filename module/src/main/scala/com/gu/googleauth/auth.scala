package com.gu.googleauth

import play.api.mvc.Results.Redirect
import play.api.mvc.{Result, RequestHeader}
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.{WSResponse, WS}
import play.api.libs.json.JsValue
import scala.language.postfixOps
import java.math.BigInteger
import java.security.SecureRandom
import play.api.Application

case class GoogleAuthConfig(clientId: String, clientSecret: String, redirectUrl: String, domain: Option[String])

class GoogleAuthException(val message: String, val throwable: Throwable = null) extends Exception(message, throwable)

object GoogleAuth {
  var discoveryDocumentHolder: Option[Future[DiscoveryDocument]] = None

  def discoveryDocument(implicit context: ExecutionContext, application: Application): Future[DiscoveryDocument] =
    if (discoveryDocumentHolder.isDefined) discoveryDocumentHolder.get
    else {
      val discoveryDocumentFuture = WS.url(DiscoveryDocument.url).get().map(r => DiscoveryDocument.fromJson(r.json))
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
                      (implicit context: ExecutionContext, application: Application): Future[Result] = {
    val queryString: Map[String, Seq[String]] = Map(
      "client_id" -> Seq(config.clientId),
      "response_type" -> Seq("code"),
      "scope" -> Seq("openid email profile"),
      "redirect_uri" -> Seq(config.redirectUrl),
      "state" -> Seq(antiForgeryToken)
    ) ++ config.domain.map(domain => "hd" -> Seq(domain))

    discoveryDocument.map(dd => Redirect(s"${dd.authorization_endpoint}", queryString))
  }

  def validatedUserIdentity(config: GoogleAuthConfig, expectedAntiForgeryToken: String)
        (implicit request: RequestHeader, context: ExecutionContext, application: Application): Future[UserIdentity] = {
    if (!request.queryString.getOrElse("state", Nil).contains(expectedAntiForgeryToken)) {
      throw new IllegalArgumentException("The anti forgery token did not match")
    } else {
      discoveryDocument.flatMap { dd =>
        val code = request.queryString("code")
        WS.url(dd.token_endpoint).post {
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
              WS.url(dd.userinfo_endpoint)
                .withHeaders("Authorization" -> s"Bearer ${token.access_token}")
                .get().map { response =>
                  googleResponse(response) { json =>
                      val userInfo = UserInfo.fromJson(json)
                      UserIdentity(
                        jwt.claims.sub,
                        jwt.claims.email,
                        userInfo.given_name,
                        userInfo.family_name,
                        jwt.claims.exp
                      )
                  }
              }
          }
        }
      }
    }
  }
}