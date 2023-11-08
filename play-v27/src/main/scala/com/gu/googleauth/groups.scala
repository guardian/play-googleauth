package com.gu.googleauth

import java.security.PrivateKey
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.directory.{Directory, DirectoryScopes}
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials

import scala.jdk.CollectionConverters._
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
@deprecated("Use com.google.auth.oauth2.ServiceAccountCredentials instead", "play-googleauth 2.1.0")
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
 * So long as you have the Service Account certificate as a string, you can easily make
 * an instance of com.google.auth.oauth2.ServiceAccountCredentials like this:
 *
 * {{{
 * import org.apache.commons.io.Charsets.UTF_8
 * import org.apache.commons.io.IOUtils
 * import com.google.auth.oauth2.ServiceAccountCredentials
 *
 * val serviceAccountCert: String = ... // certificate from Google Developers Console
 * val credentials = ServiceAccountCredentials.fromStream(IOUtils.toInputStream(serviceAccountCert, UTF_8))
 * }}}
 *
 * @param impersonatedUser a separate domain-user account email address (eg 'example@guardian.co.uk'), the email address
 *                         of the user the application will be impersonating when making calls.
 */
class GoogleGroupChecker(impersonatedUser: String, serviceAccountCredentials: ServiceAccountCredentials) {

  @deprecated(
    "this constructor is deprecated, use the constructor accepting com.google.auth.oauth2.ServiceAccountCredentials instead",
    "play-googleauth 2.1.0"
  )
  def this(googleServiceAccount: GoogleServiceAccount) = {
    this(
      googleServiceAccount.impersonatedUser,
      ServiceAccountCredentials.newBuilder()
        .setPrivateKey(googleServiceAccount.privateKey)
        .setServiceAccountUser(googleServiceAccount.email)
        .build()
    )
  }

  val directoryService: Directory = {
    val credentials = serviceAccountCredentials
      .createDelegated(impersonatedUser)
      .createScoped(DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY)
    val transport = GoogleNetHttpTransport.newTrustedTransport()
    val jsonFactory = GsonFactory.getDefaultInstance
    new Directory.Builder(transport, jsonFactory, new HttpCredentialsAdapter(credentials)).build
  }

  def retrieveGroupsFor(userEmail: String)(implicit ec: ExecutionContext): Future[Set[String]] = for {
    resp <- Future { blocking { directoryService.groups.list.setUserKey(userEmail).execute() } }
  } yield resp.getGroups.asScala.map(_.getEmail).toSet
  
}
