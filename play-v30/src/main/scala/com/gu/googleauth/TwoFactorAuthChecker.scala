package com.gu.googleauth

import com.google.api.services.directory.DirectoryScopes.ADMIN_DIRECTORY_USER_READONLY
import com.google.auth.oauth2.GoogleCredentials
import com.gu.googleauth.internal.DirectoryService

import scala.concurrent.{ExecutionContext, Future, blocking}

/**
 * Uses the `isEnrolledIn2Sv` field on https://developers.google.com/admin-sdk/directory/reference/rest/v1/users
 * to check the 2FA status of a user.
 *
 * @param googleCredentials must have read-only access to retrieve a User using the Admin SDK Directory API
 */
class TwoFactorAuthChecker(googleCredentials: GoogleCredentials) {

  private val usersApi = DirectoryService(googleCredentials, ADMIN_DIRECTORY_USER_READONLY).users()
  
  def check(userEmail: String)(implicit ec: ExecutionContext): Future[Boolean] = Future { blocking {
    usersApi.get(userEmail).execute().getIsEnrolledIn2Sv
  }}
}
