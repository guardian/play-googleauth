import com.google.auth.oauth2.ServiceAccountCredentials
import com.gu.googleauth._
import controllers.{Application, Login, routes}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, EssentialFilter, RequestHeader}
import play.api.routing.Router
import play.api.{ApplicationLoader, BuiltInComponentsFromContext, Mode}
import play.filters.HttpFiltersComponents
import router.Routes

import java.io.FileInputStream
import scala.jdk.CollectionConverters._

class AppComponents(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents{

  val googleAuthConfig = GoogleAuthConfig(
    clientId = configuration.get[String]("your.clientId.config.path"),
    clientSecret = configuration.get[String]("your.clientSecret.config.path"),
    redirectUrl = configuration.get[String]("your.redirectUrl.config.path"),
    domains = List(configuration.get[String]("your.apps-domain.config.path")),
    antiForgeryChecker = AntiForgeryChecker.borrowSettingsFromPlay(httpConfiguration)
  )

  // In Test mode this filter lets every request through without Google auth
  implicit val javaEnvironment: play.Environment = environment.asJava

  override def httpFilters: Seq[EssentialFilter] = super.httpFilters :+ new GoogleAuthFilters.AuthFilterWithExemptions(
    loginUrl = FilterExemption("/login"),
    exemptions = Seq(FilterExemption("/oauth2callback"))
  )

  val requiredGoogleGroups = configuration.underlying.getStringList("your.requiredGoogleGroups").asScala.toSet

  val credentials  =
    ServiceAccountCredentials.fromStream(new FileInputStream(configuration.get[String]("your.serviceAccountCert.path")))

  val googleGroupChecker = new GoogleGroupChecker("service.account@mydomain.com", credentials)

  val authAction = new AuthAction[AnyContent](googleAuthConfig, routes.Login.loginAction, controllerComponents.parsers.default)(executionContext) {
    // The filter above skips auth in Test mode, but actions still need an identity to run
    override def userIdentity(request: RequestHeader): Option[UserIdentity] =
      if (environment.mode == Mode.Test) Some(AppComponents.testIdentity) else super.userIdentity(request)
  }

  val login = new Login(requiredGoogleGroups, googleAuthConfig, googleGroupChecker, wsClient, controllerComponents)(executionContext)
  val appController = new Application(authAction, requiredGoogleGroups, googleAuthConfig, googleGroupChecker, controllerComponents)(executionContext)

  override def router: Router = new Routes(
    httpErrorHandler,
    appController,
    login
  )
}

object AppComponents {
  val testIdentity = UserIdentity(
    sub = "test-user",
    email = "test.user@example.com",
    firstName = "Test",
    lastName = "User",
    exp = Long.MaxValue / 1000,
    avatarUrl = None
  )
}
