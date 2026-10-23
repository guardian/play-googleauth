import play.api.{Application, ApplicationLoader, LoggerConfigurator, Mode}

class AppLoader extends ApplicationLoader{
  override def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }

    // `sbt run` always starts in Dev mode, so allow Test mode to be requested via config
    val effectiveContext =
      if (context.initialConfiguration.get[Boolean]("your.testMode"))
        context.copy(environment = context.environment.copy(mode = Mode.Test))
      else context

    val components = new AppComponents(effectiveContext)

    components.application
  }
}
