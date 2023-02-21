package portals

import portals.compiler.*

/** Application Builder Implementation. */
class ApplicationBuilderImpl(using bctx: ApplicationBuilderContext) extends ApplicationBuilder:
  override def build(): Application =
    bctx.freeze()
    CompilerBuilder.preCompiler().compile(bctx.app) // may throw exception

  override def registry: RegistryBuilder = RegistryBuilder()

  override def workflows[T, U](name: String = null): WorkflowBuilder[T, U] =
    val _name = bctx.name_or_id(name)
    val wfb = WorkflowBuilder[T, U](_name)
    bctx._workflowBuilders = bctx._workflowBuilders :+ wfb
    wfb

  override def splitters: SplitterBuilder = this.splitters(null)

  override def splitters(name: String = null): SplitterBuilder = SplitterBuilder(name)

  override def generators: GeneratorBuilder = this.generators(null)

  override def generators(name: String = null): GeneratorBuilder = GeneratorBuilder(name)

  override def sequencers: SequencerBuilder = this.sequencers(null)

  override def sequencers(name: String = null): SequencerBuilder = SequencerBuilder(name)

  override def connections: ConnectionBuilder = this.connections(null)

  override def connections(name: String = null): ConnectionBuilder = ConnectionBuilder(name)

  override def portals: PortalBuilder = this.portals(null)

  override def portals(name: String = null): PortalBuilder = PortalBuilder(name)
