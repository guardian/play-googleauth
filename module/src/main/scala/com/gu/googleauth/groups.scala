package com.gu.googleauth

import com.google.gdata.client.appsforyourdomain.AppsGroupsService

import scala.collection.convert.wrapAll._
import scala.concurrent._

/**
 * The configuration class for Google Group authentication
 * @param adminUser Administrator user which needs access to the Provisioning API
 *                  (this will need to be set up by a Domain administrator)
 * @param adminPassword Administrator password
 * @param domain Domain being configured
 * @param applicationName Application name consuming the API
 */
case class GoogleGroupConfig(
  adminUser: String,
  adminPassword: String,
  domain: String,
  applicationName: String)

class GoogleGroupChecker(config: GoogleGroupConfig) {

  val service = new AppsGroupsService(config.adminUser, config.adminPassword, config.domain, config.applicationName)

  /**
   * @param directOnly If true, members with direct association only will be considered
   */
  def retrieveGroupsFor(userEmail: String, directOnly: Boolean = false)(implicit ec: ExecutionContext): Future[Set[String]] = for {
    groups <- Future { blocking { service.retrieveGroups(userEmail, directOnly) } }
  } yield groups.getEntries.flatMap { entry =>
    Option(entry.getProperty("groupId"))
  }.toSet

}