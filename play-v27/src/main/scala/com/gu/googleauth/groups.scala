package com.gu.googleauth

import com.google.api.services.directory.Directory
import com.google.api.services.directory.DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY
import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}
import com.gu.googleauth.internal.DirectoryService

import scala.concurrent._
import scala.jdk.CollectionConverters._

/**
 * The Directory API can tell you what groups (ie Google Group) a user is in.
 *
 * You can use a Service Account to access the Directory API (in fact, non-Service access, ie web-user,
 * doesn't seem to work?). The Service Account needs the following scope:
 * https://www.googleapis.com/auth/admin.directory.group.readonly
 *
 * So long as you have the Service Account certificate as a string, you can easily make
 * an instance of com.google.auth.oauth2.ServiceAccountCredentials with
 * [[ServiceAccount.credentialsFrom(java.lang.String)]].
 *
 * @param impersonatedUser a separate domain-user account email address (eg 'example@guardian.co.uk'), the email address
 *                         of the user the application will be impersonating when making calls.
 */
class GoogleGroupChecker(impersonatedUser: String, serviceAccountCredentials: ServiceAccountCredentials) {

  private val googleCredentials: GoogleCredentials = serviceAccountCredentials.createDelegated(impersonatedUser)

  private val directoryService: Directory = DirectoryService(googleCredentials, ADMIN_DIRECTORY_GROUP_READONLY)

  def retrieveGroupsFor(userEmail: String)(implicit ec: ExecutionContext): Future[Set[String]] = for {
    resp <- Future { blocking { directoryService.groups.list.setUserKey(userEmail).execute() } }
  } yield resp.getGroups.asScala.map(_.getEmail).toSet
  
}
