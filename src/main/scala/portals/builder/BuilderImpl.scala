package portals

/** Application Builder Implementation. */
class BuilderImpl(using bctx: BuilderContext) extends Builder:
  override def build(): Application =
    new Application(
      name = bctx.name,
      workflows = bctx.workflows.map(_.build())
    )

  override def workflows[T, U](name: String): WorkflowBuilder[T, U] =
    val wb = WorkflowBuilder[T, U](name)
    bctx.workflows = wb :: bctx.workflows
    wb

  /** Check if the application is well-formed. */
  private def check(): Boolean = ???
