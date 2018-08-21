package com.gu.googleauth

import org.scalatest.{FreeSpec, Matchers}

class GoogleAuthTest extends FreeSpec with Matchers {
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
}
