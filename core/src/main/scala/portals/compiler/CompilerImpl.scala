package portals

private[portals] class CompilerImpl extends Compiler[Application, Application]:
  given ctx: CompilerContext = new CompilerContext

  def compile(application: Application): Application =
    CompilerPhases.WellFormedCheck.run(application)

end CompilerImpl // class
