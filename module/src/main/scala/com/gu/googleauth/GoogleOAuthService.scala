package com.gu.googleauth

import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

case class OAuthConfig(
  clientId: String,
  clientSecret: String,
  redirectUrl: String
)

class GoogleOAuthService(config: OAuthConfig, dd: DiscoveryDocument)(implicit context: ExecutionContext, ws: WSClient) {

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

  // https://developers.google.com/identity/protocols/OpenIDConnect#exchangecode
  def exchangeCodeForToken(code: String): Future[TokenResponse] = {
    val requestBody = Map[String, Seq[String]](
      "code" -> Seq(code),
      "client_id" -> Seq(config.clientId),
      "client_secret" -> Seq(config.clientSecret),
      "redirect_uri" -> Seq(config.redirectUrl),
      "grant_type" -> Seq("authorization_code")
    )

    for {
      response <- ws.url(dd.token_endpoint).post(requestBody)
    } yield googleResponse(response)(TokenResponse.fromJson)
  }

  // https://developers.google.com/identity/protocols/OpenIDConnect#obtaininguserprofileinformation
  def fetchUserInfo(tr: TokenResponse): Future[UserInfo] = for {
    response <- ws.url(dd.userinfo_endpoint).withHttpHeaders("Authorization" -> s"Bearer ${tr.access_token}").get()
  } yield googleResponse(response)(UserInfo.fromJson)


  def fetchUserIdentityForCode(code: String): Future[UserIdentity] = {
    val requiredDomain: Option[String]= ???
    for {
      tokenResponse <- exchangeCodeForToken(code)
      jwt = tokenResponse.jwt
//      requiredDomain foreach { domain =>
//        if (!jwt.claims.email.split("@").lastOption.contains(domain))
//          throw new GoogleAuthException("Configured Google domain does not match")
//      }
      userInfo <- jwt.claimsJson.validate[UserInfo].asOpt.map(Future.successful).getOrElse(fetchUserInfo(tokenResponse))
    } yield UserIdentity(
      jwt.claims.sub,
      jwt.claims.email,
      userInfo.given_name,
      userInfo.family_name,
      jwt.claims.exp,
      userInfo.picture
    )
  }
}
