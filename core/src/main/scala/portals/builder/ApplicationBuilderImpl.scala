package portals

/** Application Builder Implementation. */
class ApplicationBuilderImpl(using bctx: ApplicationBuilderContext) extends ApplicationBuilder:
  override def build(): Application =
    bctx.freeze()
    this.check()
    bctx.app

  override def registry: RegistryBuilder = RegistryBuilder()

  override def workflows[T, U](name: String = null): WorkflowBuilder[T, U] =
    val _name = bctx.name_or_id(name)
    val wfb = WorkflowBuilder[T, U](_name)
    bctx._workflowBuilders = bctx._workflowBuilders :+ wfb
    wfb

  override def generators: GeneratorBuilder = this.generators(null)

  override def generators(name: String = null): GeneratorBuilder = GeneratorBuilder(name)

  override def sequencers: SequencerBuilder = this.sequencers(null)

  override def sequencers(name: String = null): SequencerBuilder = SequencerBuilder(name)

  override def connections: ConnectionBuilder = this.connections(null)

  override def connections(name: String = null): ConnectionBuilder = ConnectionBuilder(name)

  override def portals: PortalBuilder = this.portals(null)

  override def portals(name: String = null): PortalBuilder = PortalBuilder(name)

  /** Check if the application is well-formed. Throws exception. */
  private def check(): Unit =
    // 1. check for naming collissions
    // X: no two distinct elements may share the same path
    val allNames = bctx.app.workflows.map(_.path)
      ++ bctx.app.generators.map(_.path)
      ++ bctx.app.streams.map(_.path)
      ++ bctx.app.sequencers.map(_.path)
      // ++ bctx.app.splitters.map(_.path)
      ++ bctx.app.connections.map(_.path)
      ++ bctx.app.portals.map(_.path)
      ++ bctx.app.externalStreams.map(_.path)
      ++ bctx.app.externalSequencers.map(_.path)
      ++ bctx.app.externalPortals.map(_.path)
    if allNames.length != allNames.toSet.toList.length then throw Exception()

    // 2. check that a portal has a single replier
    // X: If an application defines a portal, then it must also define exactly one portal handler, and vice versa.
    val portals = bctx.app.portals.map(_.path)
    val portalTasks = bctx.app.workflows.map(_.tasks).flatMap(_.values)
    val _portals = portalTasks
      .filter { case r @ ReplierTask(_, _) => true; case _ => false }
      .flatMap(_.asInstanceOf[ReplierTask[_, _, _, _]].portals)
      .map(_.path)
    if !(portals.length == _portals.length && portals.forall { _portals.contains(_) } && _portals.forall {
        portals.contains(_)
      })
    then throw Exception()
