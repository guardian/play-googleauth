package com.gu.googleauth

import com.google.auth.oauth2.ServiceAccountCredentials

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8

case object ServiceAccountHelper {
  /**
   * Constructs ServiceAccountCredentials from a certificate JSON string. This string is the contents of the
   * credentials.json file that's downloaded from the Google Developers Console when you create credentials for
   * a Service Account:
   *
   * https://developers.google.com/workspace/guides/create-credentials#create_credentials_for_a_service_account
   */
  def credentialsFrom(certificateJson: String): ServiceAccountCredentials =
    ServiceAccountCredentials.fromStream(new ByteArrayInputStream(certificateJson.getBytes(UTF_8)))
}
