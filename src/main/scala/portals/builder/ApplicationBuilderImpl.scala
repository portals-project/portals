package portals

/** Application Builder Implementation. */
class ApplicationBuilderImpl(using bctx: ApplicationBuilderContext) extends ApplicationBuilder:
  override def build(): Application =
    bctx.freeze();
    bctx.app

  override def registry: RegistryBuilder = RegistryBuilder()

  override def workflows[T, U](name: String): WorkflowBuilder[T, U] =
    val wfb = WorkflowBuilder[T, U](name)
    bctx._workflowBuilders = bctx._workflowBuilders :+ wfb
    wfb

  override def generators: GeneratorBuilder = GeneratorBuilder()

  override def sequencers: SequencerBuilder = SequencerBuilder()

  override def connections: ConnectionBuilder = ConnectionBuilder()

  override def portals: PortalBuilder = PortalBuilder()

  /** Check if the application is well-formed. */
  private def check(): Boolean = ???
