package com.gu.googleauth

import com.google.api.services.directory.Directory
import com.google.api.services.directory.DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY
import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}
import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.gu.googleauth.internal.DirectoryService

import java.time.Duration
import scala.concurrent._
import scala.jdk.CollectionConverters._

/**
 * The Directory API can tell you what groups (ie Google Group) a user is in.
 *
 * You can use a Service Account to access the Directory API (in fact, non-Service access, ie web-user,
 * doesn't seem to work?). The Service Account needs the following scope:
 * https://www.googleapis.com/auth/admin.directory.group.readonly - note that if you're using
 * [[TwoFactorAuthChecker]] it requires a different scope:
 * https://www.googleapis.com/auth/admin.directory.user.readonly
 *
 * So long as you have the Service Account certificate as a string, you can easily make
 * an instance of com.google.auth.oauth2.ServiceAccountCredentials with
 * [[ServiceAccount.credentialsFrom(java.lang.String)]].
 *
 * @param impersonatedUser a separate domain-user account email address (eg 'example@guardian.co.uk'), the email address
 *                         of the user the application will be impersonating when making calls.
 * @param serviceAccountCredentials Google OAuth2 credentials.
 * @param cacheDuration how long to cache each user's groups for (defaults to 1 minute).
 *
 */
class GoogleGroupChecker(
  impersonatedUser: String,
  serviceAccountCredentials: ServiceAccountCredentials,
  cacheDuration: Duration = Duration.ofMinutes(1)
) {

  private val googleCredentials: GoogleCredentials = serviceAccountCredentials.createDelegated(impersonatedUser)

  private val directoryService: Directory = DirectoryService(googleCredentials, ADMIN_DIRECTORY_GROUP_READONLY)

  type Email = String
  private val cache: LoadingCache[Email, Set[String]] = CacheBuilder.newBuilder()
    .expireAfterWrite(cacheDuration)
    .build(
      new CacheLoader[Email, Set[String]]() {
        def load(email: Email): Set[String] = {
          println(s"loading $email")
          val result = directoryService.groups.list.setUserKey(email).execute()
          result.getGroups.asScala.map(_.getEmail).toSet
        }
      }
    )

  def retrieveGroupsFor(userEmail: String)(implicit ec: ExecutionContext): Future[Set[String]] = Future {
    val start = System.currentTimeMillis()
    val result = blocking { cache.get(userEmail) }
    val end = System.currentTimeMillis()
    println(s"took ${end - start}")
    result
  }
}
