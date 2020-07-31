package com.gu.googleauth

import java.time.{Instant, ZonedDateTime}

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.{/, Query}
import com.gu.googleauth.GoogleAuthTest._
import com.gu.googleauth.UserIdentity.{KEY, userIdentityFormats}
import com.gu.play.secretrotation.DualSecretTransition.TransitioningSecret
import com.gu.play.secretrotation.SnapshotProvider
import mockws.{MockWS, MockWSHelpers}
import org.apache.commons.codec.binary.Base64
import org.scalatest.{AsyncFreeSpec, Inspectors, Matchers}
import org.threeten.extra.Interval
import play.api.libs.json.Json.{prettyPrint, toJson}
import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.WSClient
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, RequestHeader}
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext.global
import scala.concurrent.{ExecutionContext, Future}

class GoogleAuthTest extends AsyncFreeSpec with Matchers with MockWSHelpers {

  "enforceUserGroups" - {
    val requiredGroups = Set("required-group-1", "required-group-2")

    "returns false if the user has no groups" in {
      val userGroups = Set.empty[String]
      val result = Actions.checkGoogleGroups(userGroups, requiredGroups)
      result shouldEqual false
    }

    "returns false if the user is missing a group" in {
      val userGroups = Set(requiredGroups.head)
      val result = Actions.checkGoogleGroups(userGroups, requiredGroups)
      result shouldEqual false
    }

    "returns false if the user has other groups but is missing a required group" in {
      val userGroups = Set(requiredGroups.head, "example-group", "another-group")
      val result = Actions.checkGoogleGroups(userGroups, requiredGroups)
      result shouldEqual false
    }

    "returns true if the required groups are present" in {
      val userGroups = requiredGroups + "example-group"
      val result = Actions.checkGoogleGroups(userGroups, requiredGroups)
      result shouldEqual true
    }
  }

  "redirectToGoogle" - {

    val user = mockUserIdentity(claims)
    GoogleAuth.discoveryDocumentHolder = Some(Future.successful(googleUrls))
    implicit val noHttpCallsAllowed: WSClient = MockWS(PartialFunction.empty)
    implicit val request: RequestHeader = requestWithUser(user)


    "Generates a correct redirect URL when no particular domains are provided" in {
      val config = googleAuthConfig(domains = List.empty)

      val expectedQuery: Map[String, String] =
        Map(
          "login_hint" -> user.email,
          "scope" -> "openid email profile",
          "client_id" -> config.clientId,
          "response_type" -> "code",
        )

      doRedirect(config).map { uri =>
        uri.withQuery(Query.Empty).toString shouldBe googleUrls.authorization_endpoint
        Inspectors.forAll(expectedQuery.toList) { case (k, v) => uri.query().get(k) shouldBe Some(v) }
        uri.query().get("hd") shouldBe None
      }
    }

    "Adds an hd parameter set to the allowed domain when exactly one domain is specified" in {
      doRedirect(googleAuthConfig(domains = List("guardian.co.uk"))).map { uri =>
        uri.query().get("hd") shouldBe Some("guardian.co.uk")
      }
    }

    "Adds an hd parameter set to '*' when exactly one domain is specified" in {
      doRedirect(googleAuthConfig(domains = List("guardian.co.uk", "example.com"))).map { uri =>
        uri.query().get("hd") shouldBe Some("*")
      }
    }
  }

  "validateUserIdentity" - {

    val user = mockUserIdentity(claims)
    GoogleAuth.discoveryDocumentHolder = Some(Future.successful(googleUrls))
    implicit val request: RequestHeader = requestWithUser(user)

    implicit val authFlowWs: WSClient = MockWS {
      case ("POST", googleUrls.token_endpoint) => Action(Ok(toJson(testToken(claims))))
      case ("GET", googleUrls.userinfo_endpoint) => Action(Ok(toJson(mockUserInfo(user))))
    }

    /**
      * google echos back the state we send them which we then validate (it is a signed JWT)
      * the easiest way to assemble a valid state is to create a redirect URL
      */
    def obtainState(config: GoogleAuthConfig) =
      doRedirect(config).map(_.query().get("state").get)

    "Return a validated user when the auth flow goes okay" in {
      val noDomains = googleAuthConfig(domains = List.empty)

      for {
        state <- obtainState(noDomains)
        req = requestWithUser(user, /.withQuery(Query("state" -> state, "code" -> "foo")))
        validated  <- GoogleAuth.validatedUserIdentity(noDomains)(req, implicitly, implicitly)
      } yield validated shouldBe user
    }

    "Throw an exception when you're not in the list of allowed domains" in {
      recoverToSucceededIf[GoogleAuthException] {
        val onlyGuardian = googleAuthConfig(domains = List("guardian.co.uk"))

        for {
          state <- obtainState(onlyGuardian)
          req = requestWithUser(user, /.withQuery(Query("state" -> state, "code" -> "foo")))
          _ <- GoogleAuth.validatedUserIdentity(onlyGuardian)(req, implicitly, implicitly)
        } yield fail("This should have thrown an exception")
      }
    }

    "Let you through if you're in just one of the allowed domains" in {
      val twoDomains = googleAuthConfig(domains = List("guardian.co.uk", "example.com"))
      val nonGuardianClaims = claims.copy(email = "foo@example.com")

      for {
        state <- obtainState(twoDomains)
        req = requestWithUser(user, /.withQuery(Query("state" -> state, "code" -> "foo")))
        validated  <- GoogleAuth.validatedUserIdentity(twoDomains)(req, implicitly, implicitly)
      } yield validated.email shouldBe nonGuardianClaims.email
    }
  }
}

/**
  * This giant scary companion object contains all the mocks we need to simulate
  * the Google auth flow from within these tests.
  */
object GoogleAuthTest {

  implicit val claimWrites: Writes[JwtClaims] = Json.writes[JwtClaims]
  implicit val infoWrites: Writes[UserInfo] = Json.writes[UserInfo]
  implicit val tokenWrites: Writes[Token] = Json.writes[Token]
  private implicit val ec: ExecutionContext = global

  val tomorrowMillis: Long =
    ZonedDateTime.now.plusDays(1).toInstant.toEpochMilli

  /**
    * This is the data type that we get out of the library on a valid auth handshake
    * but it is also kept in the play session as when reauthorising we send the email to Google
    */
  def mockUserIdentity(claims: JwtClaims): UserIdentity =
    UserIdentity(
      sub = claims.sub,
      email = claims.email,
      exp = claims.exp,
      firstName = "test",
      lastName = "user",
      avatarUrl = None
    )

  /**
    * This is Google's model of the User Identity above
    * it is what comes back from their `user_identity` endpoint
    */
  def mockUserInfo(identity: UserIdentity): UserInfo =
    UserInfo(
      gender = None,
      email = identity.email,
      sub = Some(identity.sub),
      name = identity.username,
      given_name = identity.firstName,
      family_name = identity.lastName,
      picture = identity.avatarUrl,
      locale = "en_GB",
      profile = None,
      hd = None
    )

  /**
    * These claims come back from Google in a JWT we assemble below
    * The only field we care about is the email address really
    */
  val claims: JwtClaims =
    JwtClaims(
      iss = "foo",
      sub = "foo",
      azp = "foo",
      at_hash = "foo",
      email = "foo@example.com",
      email_verified = true,
      aud = "yes",
      hd = None,
      iat = Instant.now.toEpochMilli,
      exp = tomorrowMillis
    )

  private def base64Claims(claims: JwtClaims): String =
    new String(Base64.encodeBase64(Json.prettyPrint(toJson(claims)).getBytes))

  /**
    * Create a Token like the one we get back from the Google token endpoint - containing
    * a valid ID JWT token which itself contains info about the user
    */
  def testToken(claims: JwtClaims): Token =
    Token(
      id_token = s"e30=.${base64Claims(claims)}",
      access_token = "access_token",
      token_type = "token_type",
      expires_in = 36000
    )

  private val sessionId: String = "session-id"

  /**
    * Create a play request containing a previously authorised UserIdentity in the session
    * This can then be used in the `redirectToGoogle` function
    */
  def requestWithUser(userIdentity: UserIdentity, uri: Uri = /): FakeRequest[AnyContent] =
    FakeRequest("GET", uri.toString).withSession(
      KEY -> prettyPrint(toJson(userIdentity)),
      "play-googleauth-session-id" -> sessionId
    )

  val hmacSecret: SnapshotProvider =
    TransitioningSecret("hmac-secret", "hmac-secret", Interval.ALL)

  /**
    * Construct a google auth config with basic info, but allow the
    * domains to be set so we can test that we exclude people with bad email addresses
    */
  def googleAuthConfig(domains: List[String]): GoogleAuthConfig =
    GoogleAuthConfig(
      clientId = "test-client",
      clientSecret = "test-client-secret",
      redirectUrl = "http://localhost/redirect",
      antiForgeryChecker = AntiForgeryChecker(hmacSecret),
      domains = domains,
    )

  /**
    * In reality the library asks Google for the endpoints to use for the OAuth flow
    * but it stores the response in a mutable global variable we can just set in the tests
    */
  val googleUrls: DiscoveryDocument =
    DiscoveryDocument(
      authorization_endpoint = "http://localhost/authorization",
      userinfo_endpoint = "http://localhost/user",
      token_endpoint = "http://localhost/token"
    )

  /**
    * Wrapper around `redirectToGoogle` that uses Akka HTTP's Uri model
    * to obtain a decent structured representation of the redirect URL for us to run tests against
    */
  def doRedirect(config: GoogleAuthConfig)(implicit ws: WSClient, r: RequestHeader): Future[Uri] =
    for {
      req <- GoogleAuth.redirectToGoogle(config, sessionId)
      noHeader = Future.failed(new Exception("No location header found"))
      uri <- req.header.headers.get("Location").map(Future.successful).getOrElse(noHeader)
    } yield Uri.parseAbsolute(uri)
}
