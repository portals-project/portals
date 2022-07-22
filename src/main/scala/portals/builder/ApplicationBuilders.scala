package portals

object ApplicationBuilders:
  def application(name: String): ApplicationBuilder =
    val _path = "/" + name
    val _name = name
    given ApplicationBuilderContext = ApplicationBuilderContext(_path = _path, _name = _name)
    new ApplicationBuilderImpl()
