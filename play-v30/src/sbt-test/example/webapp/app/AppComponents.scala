import com.google.auth.oauth2.ServiceAccountCredentials
import com.gu.googleauth._
import com.gu.play.secretrotation.DualSecretTransition.InitialSecret
import controllers.{Application, Login, routes}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, EssentialFilter}
import play.api.routing.Router
import play.api.{ApplicationLoader, BuiltInComponentsFromContext}
import play.filters.HttpFiltersComponents
import play.filters.csp.CSPComponents
import router.Routes

import java.io.FileInputStream
import scala.jdk.CollectionConverters._

class AppComponents(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents
  with CSPComponents {

  override def httpFilters: Seq[EssentialFilter] = super.httpFilters :+ cspFilter

  val googleAuthConfig = GoogleAuthConfig(
    clientId = configuration.get[String]("your.clientId.config.path"),
    clientSecret = configuration.get[String]("your.clientSecret.config.path"),
    redirectUrl = configuration.get[String]("your.redirectUrl.config.path"),
    domains = List(configuration.get[String]("your.apps-domain.config.path")),
    antiForgeryChecker = AntiForgeryChecker(InitialSecret(configuration.get[String]("play.http.secret.key"))),
    devBypass = DevAuthBypass.forEnvironment(environment, configuration.getOptional[String]("your.dev.bypass").contains("true"))
  )

  val requiredGoogleGroups = configuration.underlying.getStringList("your.requiredGoogleGroups").asScala.toSet

  val credentials  =
    ServiceAccountCredentials.fromStream(new FileInputStream(configuration.get[String]("your.serviceAccountCert.path")))

  val googleGroupChecker = new GoogleGroupChecker(configuration.get[String]("your.googleGroupChecker.user"), credentials)

  val authAction = new AuthAction[AnyContent](googleAuthConfig, routes.Login.loginAction, controllerComponents.parsers.default)(executionContext)

  val login = new Login(requiredGoogleGroups, googleAuthConfig, googleGroupChecker, wsClient, controllerComponents)(executionContext)
  val appController = new Application(authAction, requiredGoogleGroups, googleAuthConfig, googleGroupChecker, controllerComponents)(executionContext)

  override def router: Router = new Routes(
    httpErrorHandler,
    appController,
    login
  )
}
