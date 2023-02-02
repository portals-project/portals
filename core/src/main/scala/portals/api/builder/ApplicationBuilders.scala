package portals

object ApplicationBuilders:
  def application(name: String): ApplicationBuilder =
    val _path = "/" + name
    given ApplicationBuilderContext = ApplicationBuilderContext(_path = _path)
    new ApplicationBuilderImpl()
