import play.api.{Application, ApplicationLoader, LoggerConfigurator}

class AppLoader extends ApplicationLoader{
  override def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }

    val components = new AppComponents(context)

    components.application
  }
}
