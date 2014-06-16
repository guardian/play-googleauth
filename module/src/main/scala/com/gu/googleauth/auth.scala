package com.gu.googleauth

import play.api.mvc.Results.Redirect
import play.api.mvc.{SimpleResult, RequestHeader}
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.WS

case class GoogleAuthConfig(clientId: String, clientSecret:String, redirectUrl: String, domain: Option[String])

trait GoogleAuth {
  def RedirectToGoogle(discoveryDocument: DiscoveryDocument, config: GoogleAuthConfig, antiForgeryToken: String): SimpleResult = {
    val queryString: Map[String, Seq[String]] = Map(
      "client_id" -> Seq(config.clientId),
      "response_type" -> Seq("code"),
      "scope" -> Seq("openid email profile"),
      "redirect_uri" -> Seq(config.redirectUrl),
      "state" -> Seq(antiForgeryToken)
    ) ++ config.domain.map(domain => "hd" -> Seq(domain))

    Redirect(s"${discoveryDocument.authorization_endpoint}", queryString)
  }

  def ValidatedUserIdentity(discoveryDocument:DiscoveryDocument, config:GoogleAuthConfig, expectedAntiForgeryToken:String)
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
      }.flatMap {
        response =>
          val token = Token.fromJson(response.json)
          val jwt = token.jwt
          WS.url(discoveryDocument.userinfo_endpoint).withHeaders("Authorization" -> s"Bearer ${token.access_token}").get().map {
            response =>
              val userInfo = UserInfo.fromJson(response.json)
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