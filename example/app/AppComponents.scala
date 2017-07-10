import java.io.FileInputStream

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.gu.googleauth.{AuthAction, GoogleAuthConfig, GoogleServiceAccount}
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

  val clientId: String = configuration.get[String]("your.clientId.config.path")
  val clientSecret: String = configuration.get[String]("your.clientSecret.config.path")
  val redirectUrl: String = configuration.get[String]("your.redirectUrl.config.path")
  val domain: String = configuration.get[String]("your.apps-domain.config.path")
  val googleAuthConfig = GoogleAuthConfig(clientId, clientSecret, redirectUrl, domain)

  val googleCredentialLocation = configuration.get[String]("your.serviceAccountCert.path")
  val googleCredential = GoogleCredential.fromStream(new FileInputStream(googleCredentialLocation))
  val googleServiceAccount = GoogleServiceAccount(googleCredential.getServiceAccountId, googleCredential.getServiceAccountPrivateKey, "service.account@mydomain.com")

  val authAction = new AuthAction[AnyContent](googleAuthConfig, routes.Login.loginAction(), controllerComponents.parsers.default, executionContext)

  val login = new Login(wsClient, googleAuthConfig, googleServiceAccount, controllerComponents)(executionContext)
  val appController = new Application(authAction, controllerComponents)

  override def router: Router = new Routes(
    httpErrorHandler,
    appController,
    login
  )
}
