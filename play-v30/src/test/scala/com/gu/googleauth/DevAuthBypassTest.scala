package com.gu.googleauth

import com.gu.play.secretrotation.DualSecretTransition.InitialSecret
import com.gu.play.secretrotation.SnapshotProvider
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.Mode
import play.api.mvc.{Call, Result}
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class DevAuthBypassTest extends AnyFreeSpec with Matchers {

  "DevAuthBypass.forMode" - {
    "never yields a bypass in Prod, and fails fast if enabled" in {
      DevAuthBypass.forMode(Mode.Prod, enabled = false) shouldBe None
      an[IllegalStateException] should be thrownBy DevAuthBypass.forMode(Mode.Prod, enabled = true)
    }

    "requires the explicit flag in Dev and Test" in {
      for (mode <- Seq(Mode.Dev, Mode.Test)) {
        DevAuthBypass.forMode(mode, enabled = false) shouldBe None
        DevAuthBypass.forMode(mode, enabled = true).map(_.identity) shouldBe Some(DevAuthBypass.defaultIdentity)
      }
    }

    "uses a fake identity that is currently valid" in {
      DevAuthBypass.defaultIdentity.isValid shouldBe true
      DevAuthBypass.defaultIdentity.emailDomain shouldBe "example.invalid"
    }
  }

  "userIdentity" - {
    def config(bypass: Option[DevAuthBypass]) = GoogleAuthConfig(
      "id", "secret", "http://localhost/callback", List("example.com"),
      antiForgeryChecker = AntiForgeryChecker(InitialSecret("secret")),
      devBypass = bypass
    )

    def identifier(bypass: Option[DevAuthBypass]) = new UserIdentifier { val authConfig = config(bypass) }

    "is absent for an unauthenticated request without a bypass" in {
      identifier(None).userIdentity(FakeRequest()) shouldBe None
    }

    "is the fake identity with a bypass" in {
      val bypass = DevAuthBypass.forMode(Mode.Dev, enabled = true)
      identifier(bypass).userIdentity(FakeRequest()) shouldBe Some(DevAuthBypass.defaultIdentity)
    }

    "AuthAction redirects without a bypass and lets the request through with one" in {
      def run(bypass: Option[DevAuthBypass]): Result = {
        val action = new AuthAction[play.api.mvc.AnyContent](
          config(bypass), Call("GET", "/login"), play.api.test.Helpers.stubControllerComponents().parsers.default
        )
        Await.result(action(_ => play.api.mvc.Results.Ok("hi"))(FakeRequest()), 5.seconds)
      }
      run(None).header.status shouldBe 303
      run(DevAuthBypass.forMode(Mode.Test, enabled = true)).header.status shouldBe 200
    }
  }
}
