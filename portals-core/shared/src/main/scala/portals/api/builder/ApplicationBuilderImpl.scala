package portals.api.builder

import portals.application.*
import portals.compiler.*

/** Application Builder Implementation. */
private[portals] class ApplicationBuilderImpl(using val bctx: ApplicationBuilderContext) extends ApplicationBuilder:
  override def build(): Application =
    bctx.freeze()
    // may throw exception
    CompilerBuilder
      .preCompiler()
      .compile(bctx.app)

  override def registry: RegistryBuilder = RegistryBuilder()

  override def workflows[T, U]: WorkflowBuilder[T, U] = this.workflows(null)

  override def workflows[T, U](name: String = null): WorkflowBuilder[T, U] =
    val _name = bctx.name_or_id(name)
    val wfb = WorkflowBuilder[T, U](_name)
    // added to the context, so that we later can check if it was frozen or not,
    // if it wasn't frozen, then we try to freeze it when the application is built
    bctx._workflowBuilders = bctx._workflowBuilders :+ wfb
    wfb

  override def splitters: SplitterBuilder = this.splitters(null)

  override def splitters(name: String = null): SplitterBuilder = SplitterBuilder(name)

  override def splits: SplitBuilder = this.splits(null)

  override def splits(name: String = null): SplitBuilder = SplitBuilder(name)

  override def generators: GeneratorBuilder = this.generators(null)

  override def generators(name: String = null): GeneratorBuilder = GeneratorBuilder(name)

  override def sequencers: SequencerBuilder = this.sequencers(null)

  override def sequencers(name: String = null): SequencerBuilder = SequencerBuilder(name)

  override def connections: ConnectionBuilder = this.connections(null)

  override def connections(name: String = null): ConnectionBuilder = ConnectionBuilder(name)

  override def portals: PortalBuilder = this.portals(null)

  override def portals(name: String = null): PortalBuilder = PortalBuilder(name)
