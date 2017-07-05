import com.gu.googleauth.{AuthAction, GoogleAuthConfig}
import controllers.{Application, Login, routes}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.AnyContent
import play.api.routing.Router
import play.api.{ApplicationLoader, BuiltInComponentsFromContext}
import play.filters.HttpFiltersComponents
import router.Routes

class AppComponents(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents{

  lazy val clientId: String = configuration.get[String]("your.clientId.config.path")
  lazy val clientSecret: String = configuration.get[String]("your.clientSecret.config.path")
  lazy val redirectUrl: String = configuration.get[String]("your.redirectUrl.config.path")
  lazy val domain: String = configuration.get[String]("your.apps-domain.config.path")
  lazy val googleAuthConfig = GoogleAuthConfig(clientId, clientSecret, redirectUrl, domain)

  val authAction = new AuthAction[AnyContent](googleAuthConfig, routes.Login.loginAction())(
    controllerComponents.parsers.default, executionContext)

  val login = new Login(wsClient, googleAuthConfig, controllerComponents)(executionContext)
  val appController = new Application(authAction, controllerComponents)

  override def router: Router = new Routes(
    httpErrorHandler,
    appController,
    login
  )
}
