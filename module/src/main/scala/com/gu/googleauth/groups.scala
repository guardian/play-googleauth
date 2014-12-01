package com.gu.googleauth

import com.google.gdata.client.appsforyourdomain.AppsGroupsService
import scala.collection.JavaConverters._

/**
 * The configuration class for Google Group authentication
 * @param adminUser Administrator user which needs access to the Provisioning API
 *                  (this will need to be set up by a Domain administrator)
 * @param adminPassword Administrator password
 * @param domain Domain being configured
 * @param applicationName Application name consuming the API
 * @param directOnly If true, members with direct association only will be considered
 */
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