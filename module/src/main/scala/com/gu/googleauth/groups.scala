package com.gu.googleauth

import java.security.PrivateKey

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.admin.directory.{Directory, DirectoryScopes}

import scala.collection.JavaConverters._
import scala.concurrent._

/**
 * A Service Account calls Google APIs on behalf of your application instead of an end-user.
 * https://developers.google.com/identity/protocols/OAuth2#serviceaccount
 *
 * You can create a service account in the Google Developers Console:
 *
 * https://developers.google.com/identity/protocols/OAuth2ServiceAccount#creatinganaccount
 *
 * @param email email address of the Service Account
 * @param privateKey the Service Account's private key - from the P12 file generated when the Service Account was created
 * @param impersonatedUser the email address of the user the application will be impersonating
 */
case class GoogleServiceAccount(
  email: String,
  privateKey: PrivateKey,
  impersonatedUser: String
)

/**
 * The Directory API can tell you what groups (ie Google Group) a user is in.
 *
 * You can use a Service Account to access the Directory API (in fact, non-Service access, ie web-user,
 * doesn't seem to work?). The Service Account needs the following scope:
 * https://www.googleapis.com/auth/admin.directory.group.readonly
 *
 * You also need a separate domain user account (eg example@guardian.co.uk), which
 * will be 'impersonated' when making the calls.
 */
class GoogleGroupChecker(directoryServiceAccount: GoogleServiceAccount) {

  val directoryService = {
    val transport = GoogleNetHttpTransport.newTrustedTransport()
    val jsonFactory = JacksonFactory.getDefaultInstance

    val credential = new GoogleCredential.Builder()
      .setTransport(transport)
      .setJsonFactory(jsonFactory)
      .setServiceAccountId(directoryServiceAccount.email)
      .setServiceAccountUser(directoryServiceAccount.impersonatedUser)
      .setServiceAccountPrivateKey(directoryServiceAccount.privateKey)
      .setServiceAccountScopes(Seq(DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY).asJava)
      .build()

    new Directory.Builder(transport, jsonFactory, null).setHttpRequestInitializer(credential).build
  }

  def retrieveGroupsFor(userEmail: String)(implicit ec: ExecutionContext): Future[Set[String]] = for {
    resp <- Future { blocking { directoryService.groups.list.setUserKey(userEmail).execute() } }
  } yield resp.getGroups.asScala.map(_.getEmail).toSet
  
}
