package com.gu.googleauth.internal

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.directory.Directory
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials

object DirectoryService {
  def apply(googleCredentials: GoogleCredentials, scope: String): Directory = {
    val credentials = googleCredentials.createScoped(scope)
    val transport = GoogleNetHttpTransport.newTrustedTransport()
    val jsonFactory = GsonFactory.getDefaultInstance
    new Directory.Builder(transport, jsonFactory, new HttpCredentialsAdapter(credentials))
      .setApplicationName("play-googleauth").build()
  }
}
