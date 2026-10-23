import play.api.{Application, ApplicationLoader, LoggerConfigurator, Mode}

class AppLoader extends ApplicationLoader{
  override def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }

    // A staged app always starts in Prod mode; this lets the e2e tests run it in Dev mode, where the bypass is allowed
    val environment =
      if (sys.env.get("EXAMPLE_PLAY_MODE").contains("dev")) context.environment.copy(mode = Mode.Dev)
      else context.environment

    val components = new AppComponents(context.copy(environment = environment))

    components.application
  }
}
