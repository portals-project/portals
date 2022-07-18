package portals

object Builders:
  def application(_name: String): Builder =
    given BuilderContext = new BuilderContext { override val name: String = _name }
    new BuilderImpl()
