package com.gu.googleauth

import play.api.libs.json.{JsValue, Json}
import org.apache.commons.codec.binary.Base64

case class DiscoveryDocument(authorization_endpoint: String, token_endpoint: String, userinfo_endpoint: String)
object DiscoveryDocument {
  implicit val discoveryDocumentReads = Json.reads[DiscoveryDocument]
  def fromJson(json: JsValue) = Json.fromJson[DiscoveryDocument](json).getOrElse(
    throw new IllegalArgumentException("Invalid discovery document")
  )
}

case class Token(access_token:String, token_type:String, expires_in:Long, id_token:String) {
  val jwt = JsonWebToken(id_token)
}
object Token {
  implicit val tokenReads = Json.reads[Token]
  def fromJson(json:JsValue):Token = Json.fromJson[Token](json).get
}

case class JwtClaims(iss: String, sub:String, azp: String, email: String, at_hash: String, email_verified: Boolean,
                     aud: String, hd: Option[String], iat: Long, exp: Long)
object JwtClaims {
  implicit val claimsReads = Json.reads[JwtClaims]
}

case class UserInfo(kind: String, gender: Option[String], sub: String, name: String, given_name: String, family_name: String,
                    profile: String, picture: String, email: String, email_verified: String, locale: String, hd: String)
object UserInfo {
  implicit val userInfoReads = Json.reads[UserInfo]
  def fromJson(json:JsValue):UserInfo = json.as[UserInfo]
}

case class JsonWebToken(jwt: String) {
  val jwtParts: Array[String] = jwt.split('.')
  val Array(headerJson, claimsJson) = jwtParts.take(2).map(Base64.decodeBase64).map(Json.parse)
  val claims = claimsJson.as[JwtClaims]
}