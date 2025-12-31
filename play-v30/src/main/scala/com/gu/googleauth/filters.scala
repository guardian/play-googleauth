package com.gu.googleauth

import org.apache.pekko.stream.Materializer
import play.api.mvc.Results.Redirect
import play.api.mvc.{Filter, RequestHeader, Result}
import play.Environment

import scala.concurrent.Future

case class FilterExemption(path: String)

object GoogleAuthFilters {
  val LOGIN_ORIGIN_KEY = "loginOriginUrl"
  class AuthFilterWithExemptions(
      loginUrl: FilterExemption,
      exemptions: Seq[FilterExemption])(implicit val mat: Materializer, environment: Environment) extends Filter {

    def apply(nextFilter: (RequestHeader) => Future[Result])
             (requestHeader: RequestHeader): Future[Result] = {

      if (environment.isTest || requestHeader.path.startsWith(loginUrl.path) ||
        exemptions.exists(exemption => requestHeader.path.startsWith(exemption.path)))
        nextFilter(requestHeader)
      else {
        UserIdentity.fromRequest(requestHeader) match {
          case Some(identity) if identity.isValid => nextFilter(requestHeader)
          case otherIdentity =>
            Future.successful(Redirect(loginUrl.path)
              .addingToSession((LOGIN_ORIGIN_KEY, requestHeader.uri))(requestHeader))
        }
      }
    }
  }
}