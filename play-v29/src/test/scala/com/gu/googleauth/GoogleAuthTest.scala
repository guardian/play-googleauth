package com.gu.googleauth

import com.gu.play.secretrotation.DualSecretTransition.TransitioningSecret
import com.gu.play.secretrotation.SnapshotProvider
import com.gu.googleauth.Actions.GroupCheckConfig
import org.apache.commons.codec.binary.Base64
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.threeten.extra.Interval
import play.api.libs.json.Json.toJson
import play.api.libs.json.{Json, Writes}

import java.time.{Instant, ZonedDateTime}
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

class GoogleAuthTest extends AsyncFreeSpec with Matchers {

  "requiredGroups check" - {
    val requiredGroups = Set("required-group-1", "required-group-2")

    "returns false if the user has no groups" in {
      val userGroups = Set.empty[String]
      val result = Actions.checkGoogleGroups(userGroups, GroupCheckConfig(requiredGroups = Some(requiredGroups)))
      result shouldEqual false
    }

    "returns false if the user is missing a group" in {
      val userGroups = Set(requiredGroups.head)
      val result = Actions.checkGoogleGroups(userGroups, GroupCheckConfig(requiredGroups = Some(requiredGroups)))
      result shouldEqual false
    }

    "returns false if the user has other groups but is missing a required group" in {
      val userGroups = Set(requiredGroups.head, "example-group", "another-group")
      val result = Actions.checkGoogleGroups(userGroups, GroupCheckConfig(requiredGroups = Some(requiredGroups)))
      result shouldEqual false
    }

    "returns true if the required groups are present" in {
      val userGroups = requiredGroups + "example-group"
      val result = Actions.checkGoogleGroups(userGroups, GroupCheckConfig(requiredGroups = Some(requiredGroups)))
      result shouldEqual true
    }
  }

  "allowedGroups check" - {
    val requiredGroups = Set("required-group-1", "required-group-2")
    val allowedGroups = Set("allowed-group-1", "allowed-group-2")

    "returns false if the user has no groups" in {
      val userGroups = Set.empty[String]
      val result = Actions.checkGoogleGroups(userGroups, GroupCheckConfig(allowedGroups = Some(allowedGroups)))
      result shouldEqual false
    }

    "returns false if the user has other groups but is missing all allowed groups" in {
      val userGroups = Set("example-group", "another-group")
      val result = Actions.checkGoogleGroups(userGroups, GroupCheckConfig(allowedGroups = Some(allowedGroups)))
      result shouldEqual false
    }

    "returns true if the user has one group and is missing another group" in {
      val userGroups = Set(allowedGroups.head)
      val result = Actions.checkGoogleGroups(userGroups, GroupCheckConfig(allowedGroups = Some(allowedGroups)))
      result shouldEqual true
    }

    "returns false if the user has an allowed group but is missing required groups" in {
      val userGroups = Set(allowedGroups.head)
      val result = Actions.checkGoogleGroups(userGroups, GroupCheckConfig(allowedGroups = Some(allowedGroups), requiredGroups = Some(requiredGroups)))
      result shouldEqual false
    }

    "returns true if the user has an allowed group and has required groups" in {
      val userGroups = Set(allowedGroups.head) ++ requiredGroups
      val result = Actions.checkGoogleGroups(userGroups, GroupCheckConfig(allowedGroups = Some(allowedGroups), requiredGroups = Some(requiredGroups)))
      result shouldEqual true
    }
  }
}
