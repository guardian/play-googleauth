package com.gu.googleauth

import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.gu.play.secretrotation.DualSecretTransition.InitialSecret
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.{Environment, Mode}
import play.api.mvc.{Call, Result}
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.jdk.CollectionConverters._

class DevAuthBypassTest extends AnyFreeSpec with Matchers {

  private val modes = Seq(Mode.Dev, Mode.Test, Mode.Prod)
  private val validEmails = Seq(
    "tester@example.invalid",
    "tester@sub.example.invalid",
    "test.user+ci@my-app.invalid",
    "tester@a.invalid"
  )
  private val invalidEmails = Seq(
    "",
    "tester@example.com",
    "tester@example.invalid.com",
    "tester@exampleinvalid",
  )

  private def bypass(
    mode: Mode,
    enabled: Boolean = true,
    identity: UserIdentity = DevAuthBypass.defaultIdentity
  ): Option[DevAuthBypass] =
    DevAuthBypass.forEnvironment(Environment.simple(mode = mode), enabled, identity)

  private def withBypassLogs[A](operation: => A): (A, List[ILoggingEvent]) =
    play.api.Logger(DevAuthBypass.getClass).underlyingLogger match {
      case logger: Logger =>
        val appender = new ListAppender[ILoggingEvent]()
        val previousLevel = logger.getLevel
        val previousAdditive = logger.isAdditive
        appender.start()
        logger.setLevel(Level.WARN)
        logger.setAdditive(false)
        logger.addAppender(appender)
        try {
          val result = operation
          (result, appender.list.asScala.toList)
        } finally {
          val _ = logger.detachAppender(appender)
          logger.setLevel(previousLevel)
          logger.setAdditive(previousAdditive)
          appender.stop()
        }
      case _ => fail("These tests require the existing Logback test logging provider")
    }

  "DevAuthBypass.forEnvironment" - {
    "requires an explicit enabled flag" in {
      assertDoesNotCompile("""
        com.gu.googleauth.DevAuthBypass.forEnvironment(play.api.Environment.simple())
      """)
    }

    for {
      mode <- modes
      email <- validEmails ++ invalidEmails
    } {
      s"does nothing when disabled in $mode with '${email.replace("\n", "\\n")}'" in {
        val identity = DevAuthBypass.defaultIdentity.copy(email = email)
        val (result, events) = withBypassLogs(bypass(mode, enabled = false, identity = identity))
        result shouldBe None
        events shouldBe empty
      }
    }

    for (email <- validEmails ++ invalidEmails) {
      s"rejects Prod before identity validation with '${email.replace("\n", "\\n")}'" in {
        val identity = DevAuthBypass.defaultIdentity.copy(email = email)
        val (error, events) = withBypassLogs {
          intercept[IllegalStateException](bypass(Mode.Prod, identity = identity))
        }
        error.getMessage should include ("Prod")
        events shouldBe empty
      }
    }

    for (mode <- Seq(Mode.Dev, Mode.Test)) {
      s"uses a valid, reserved default identity in $mode" in {
        val (result, events) = withBypassLogs {
          DevAuthBypass.forEnvironment(Environment.simple(mode = mode), enabled = true)
        }
        result.map(_.identity) shouldBe Some(DevAuthBypass.defaultIdentity)
        result.map(_.identity.isValid) shouldBe Some(true)
        result.map(_.identity.emailDomain) shouldBe Some("example.invalid")
        events.map(_.getLevel) shouldBe List(Level.WARN)
      }
    }

    for {
      mode <- Seq(Mode.Dev, Mode.Test)
      email <- validEmails
    } {
      s"preserves a custom identity and warns when enabled in $mode with '$email'" in {
        val identity = DevAuthBypass.defaultIdentity.copy(sub = "ci-user", email = email, firstName = "CI")
        val (result, events) = withBypassLogs(bypass(mode, identity = identity))
        result.map(_.identity) shouldBe Some(identity)
        events.map(_.getLevel) shouldBe List(Level.WARN)
        events.head.getFormattedMessage should include ("BYPASS IS ACTIVE")
        events.head.getFormattedMessage should include ("Every request is treated as authenticated")
        events.head.getFormattedMessage should include ("Google Group checks are skipped")
        events.head.getFormattedMessage should include ("NEVER enable this in a production application")
      }
    }

    for {
      mode <- Seq(Mode.Dev, Mode.Test)
      email <- invalidEmails
    } {
      s"rejects an invalid email in $mode: '${email.replace("\n", "\\n")}'" in {
        val identity = DevAuthBypass.defaultIdentity.copy(email = email)
        val (error, events) = withBypassLogs {
          intercept[IllegalArgumentException](bypass(mode, identity = identity))
        }
        error.getMessage should include (".invalid")
        events shouldBe empty
      }
    }
  }

  private def config(bypass: Option[DevAuthBypass]): GoogleAuthConfig = GoogleAuthConfig(
    "id", "secret", "http://localhost/callback", List("example.com"),
    antiForgeryChecker = AntiForgeryChecker(InitialSecret("secret")),
    devBypass = bypass
  )

  "userIdentity" - {
    def identifier(bypass: Option[DevAuthBypass]) = new UserIdentifier { val authConfig = config(bypass) }

    "is absent for an unauthenticated request without a bypass" in {
      identifier(None).userIdentity(FakeRequest()) shouldBe None
    }

    "is the fake identity with a bypass" in {
      identifier(bypass(Mode.Dev)).userIdentity(FakeRequest()) shouldBe Some(DevAuthBypass.defaultIdentity)
    }
  }

  "AuthAction" - {
    def run(bypass: Option[DevAuthBypass]): Result = {
      val action = new AuthAction[play.api.mvc.AnyContent](
        config(bypass), Call("GET", "/login"), play.api.test.Helpers.stubControllerComponents().parsers.default
      )
      Await.result(action(_ => play.api.mvc.Results.Ok("Hello"))(FakeRequest()), 5.seconds)
    }

    "redirects without a bypass" in {
      run(None).header.status shouldBe 303
    }

    "lets request with a bypass through" in {
      run(bypass(Mode.Test)).header.status shouldBe 200
    }
  }
}
