package com.gu.googleauth

import com.google.gdata.client.appsforyourdomain.AppsGroupsService
import scala.collection.JavaConverters._

case class GoogleGroupConfig(
  adminUser: String,
  adminPassword: String,
  domain: String,
  applicationName: String,
  directOnly: Boolean = false)

object GoogleGroupChecker {

  def userIsInGroup(config: GoogleGroupConfig, userEmail: String, groupEmail: String): Boolean = {
    val service = new AppsGroupsService(config.adminUser, config.adminPassword, config.domain, config.applicationName)
    getGroupIds(service, config.directOnly, userEmail).contains(groupEmail)
  }

  private def getGroupIds(service: AppsGroupsService, directOnly: Boolean, userEmail: String): Set[String] =
    service.retrieveGroups(userEmail, directOnly).getEntries.asScala.flatMap { entry =>
      Option(entry.getProperty("groupId"))
    }.toSet
}