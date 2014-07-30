package com.gu.googleauth

import play.api.mvc.Results.Redirect
import play.api.mvc.{Filter, RequestHeader, Result}
import scala.concurrent.Future

case class FilterExemption(url: String)

object GoogleAuthFilters {
  class AuthFilterWithExemptions(
      loginUrl: FilterExemption,
      exemptions: Seq[FilterExemption]) extends Filter {

    def apply(nextFilter: (RequestHeader) => Future[Result])
             (requestHeader: RequestHeader): Future[Result] = {

      if (requestHeader.path.startsWith(loginUrl.url) ||
        exemptions.exists(exemption => requestHeader.path.startsWith(exemption.url)))
        nextFilter(requestHeader)
      else {
        UserIdentity.fromRequest(requestHeader) match {
          case Some(identity) if identity.isValid => nextFilter(requestHeader)
          case otherIdentity =>
            Future.successful(Redirect(loginUrl.url))
        }
      }
    }
  }
}