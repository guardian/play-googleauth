package com.gu.googleauth

import play.api.mvc.Results.Redirect
import play.api.mvc.{SimpleResult, RequestHeader}
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.{Response, WS}
import play.api.libs.json.JsValue

case class GoogleAuthConfig(clientId: String, clientSecret:String, redirectUrl: String, domain: Option[String])

class GoogleAuthException(val message: String, val throwable: Throwable = null) extends Exception(message, throwable)

object GoogleAuth {
  def googleResponse[T](r:Response)(block:JsValue => T): T = {
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

  def redirectToGoogle(discoveryDocument: DiscoveryDocument, config: GoogleAuthConfig, antiForgeryToken: String): SimpleResult = {
    val queryString: Map[String, Seq[String]] = Map(
      "client_id" -> Seq(config.clientId),
      "response_type" -> Seq("code"),
      "scope" -> Seq("openid email profile"),
      "redirect_uri" -> Seq(config.redirectUrl),
      "state" -> Seq(antiForgeryToken)
    ) ++ config.domain.map(domain => "hd" -> Seq(domain))

    Redirect(s"${discoveryDocument.authorization_endpoint}", queryString)
  }

  def validatedUserIdentity(discoveryDocument:DiscoveryDocument, config:GoogleAuthConfig, expectedAntiForgeryToken:String)
                  (implicit request:RequestHeader, context:ExecutionContext): Future[UserIdentity] = {
    if (!request.queryString.getOrElse("state", Nil).contains(expectedAntiForgeryToken)) {
      throw new IllegalArgumentException("The anti forgery token did not match")
    } else {
      val code = request.queryString("code")
      WS.url(discoveryDocument.token_endpoint).post {
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
          WS.url(discoveryDocument.userinfo_endpoint)
            .withHeaders("Authorization" -> s"Bearer ${token.access_token}")
            .get().map {
            response =>
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