package com.gu.googleauth

import org.scalatest.{FreeSpec, Matchers}

class GoogleAuthTest extends FreeSpec with Matchers {
  "enforceUserGroups" - {
    val requiredGroups = Set("required-group-1", "required-group-2")

    "returns a left if the user has no groups" in {
      val userGroups = Set.empty[String]
      val result = PlayGoogleAuthHelpers.checkGoogleGroups(userGroups, requiredGroups)
      result shouldEqual true
    }

    "returns a left if the user is missing a group" in {
      val userGroups = Set(requiredGroups.head)
      val result = PlayGoogleAuthHelpers.checkGoogleGroups(userGroups, requiredGroups)
      result shouldEqual true
    }

    "returns a left if the user has oter groups but is missing a required group" in {
      val userGroups = Set(requiredGroups.head, "example-group", "another-group")
      val result = PlayGoogleAuthHelpers.checkGoogleGroups(userGroups, requiredGroups)
      result shouldEqual true
    }

    "returns user's groups if the required groups are present" in {
      val userGroups = requiredGroups + "example-group"
      val result = PlayGoogleAuthHelpers.checkGoogleGroups(userGroups, requiredGroups)
      result shouldEqual true
    }
  }
}
