package com.gu.googleauth

import com.google.auth.oauth2.GoogleCredentials
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProvider, ProfileCredentialsProvider}
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region.EU_WEST_1
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

/**
 * This is a non-production piece of code, only intended for checking that Google Credentials are correctly set up,
 * and that we're able to get a good response from the Google Admin SDK Directory API.
 */
object TwoFactorAuthCheckerCLITester extends App {

  val checker = new TwoFactorAuthChecker(GoogleCredentialsInDev.loadGuardianGoogleCredentials())

  val userEmail = ??? // eg "firstname.lastname@guardian.co.uk"

  val has2FA: Boolean = Await.result(checker.check(userEmail), Duration.Inf)

  println(s"User $userEmail has 2FA enabled: $has2FA")
}

object GoogleCredentialsInDev {
  /**
   * Only intended for use by Guardian developers, in development. Requires Janus credentials.
   */
  def loadGuardianGoogleCredentials(): GoogleCredentials = {
    val aws = new AWS("ophan")
    val serviceAccountCert = aws.loadSecureString("/Ophan/Dashboard/GoogleCloudPlatform/OphanOAuthServiceAccountCert")
    val impersonatedUser = aws.loadSecureString("/Ophan/Dashboard/GoogleCloudPlatform/ImpersonatedUser")

    ServiceAccountHelper.credentialsFrom(serviceAccountCert).createDelegated(impersonatedUser)
  }
}

class AWS(profileName: String) {
  val credentials: AwsCredentialsProvider = ProfileCredentialsProvider.builder().profileName(profileName).build()

  val SSM: SsmClient =
    SsmClient.builder().httpClientBuilder(ApacheHttpClient.builder()).credentialsProvider(credentials).region(EU_WEST_1).build()

  def loadSecureString(paramName: String): String = SSM.getParameter(
    GetParameterRequest.builder().name(paramName).withDecryption(true).build()
  ).parameter().value()
}