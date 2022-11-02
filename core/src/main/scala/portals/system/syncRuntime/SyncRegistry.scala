package portals

// trait SequencerFactory {
//   def create(name: String): Sequencer
// }

// TODO move to a separate file
class SyncRegistry extends GlobalRegistry:
  var sequencers = Map[String, SyncSequencer]()
  var workflows = Map[String, SyncWorkflow]()
  var generators = Map[String, SyncGenerator]()
  var portals = Map[String, RuntimePortal]()

  private val logger = Logger("syncRegistry")
  var hangingConnections = Set[AtomicConnection[_]]()
//   var executables = List[Executable]()

  def addPortal(portal: AtomicPortal[_, _]): Unit =
    val rtportal = RuntimePortal(portal)
    portals += (portal.path -> rtportal)

  def addSequencer[T](app: Application, seq: AtomicSequencer[_]): Unit = {
    // find out how many upstreams this sequencer has
    // val upStreams =
    //   app.connections.filter(c => c.ti.path == seq.path).map(c => c.from).asInstanceOf[List[AtomicStreamRef[T]]]
    val rtsq = new RuntimeSequencer(seq.asInstanceOf[AtomicSequencer[T]])
    sequencers += seq.stream.path -> rtsq
    // executables = executables :+ rtsq
  }

  def addWorkflow(app: Application, wf: Workflow[_, _]): Unit = {
    val rtwf = RuntimeWorkflow.fromStaticWorkflow(app, wf)
    workflows += (wf.stream.path -> rtwf)
    // executables = executables :+ rtwf
  }

  def addGenerator(gen: AtomicGenerator[_]): Unit = {
    val rtgen = RuntimeGenerator(gen)
    generators += gen.stream.path -> rtgen
    // executables = executables :+ rtgen
  }

  def getSequencer[T](stream: AtomicStreamRefKind[_]): Option[SyncSequencer] = {
    sequencers.get(stream.path)
  }

  def getWorkflow[T](stream: AtomicStreamRefKind[_]): Option[SyncWorkflow] = {
    workflows.get(stream.path)
  }

  def getGenerator[T](stream: AtomicStreamRefKind[_]): Option[SyncGenerator] = {
    generators.get(stream.path)
  }

  def launch(application: Application): Unit =
    application.sequencers.foreach {
      addSequencer(application, _)
    }

    application.generators.foreach(g => addGenerator(g))

    application.portals.foreach { portal => addPortal(portal) }

    // launch workflows
    application.workflows.foreach(workflow => addWorkflow(application, workflow))

    // register workflows to portals
    application.workflows.foreach { workflow =>
      val portalDeps = workflow.tasks.flatMap { (name, task) =>
        task match
          case at @ ReplierTask(_, _) => at.portals
          case _ => List.empty
      }.toList

      portalDeps.foreach { p =>
        portals
          .get(p.path)
          .get
          .subscribe(
            workflows.get(workflow.path).get
          )
      }
    }

    // register workflow to whom it consumes
    application.workflows.foreach(workflow =>
      val runtimeWf = getWorkflow(workflow.stream).get
      logger.debug(s"${runtimeWf.staticWf.path} subscribes to ${workflow.consumes.path} ")
      addSubscriber(workflow.consumes, runtimeWf)
    )

    var hangingConnectionsToDel = Set[AtomicConnection[_]]()
    hangingConnections.foreach(c => {
      if (sequencers.contains(c.to.stream.path)) {
        hangingConnectionsToDel += c

        logger.debug(s"${c.to.stream.path} subscribes to ${c.from.path} (hanging)")
        val dstSequencer = getSequencer(c.to.stream).get
        dstSequencer.subscribe(c.from) // sequencer's aspect
        addSubscriber(c.from, dstSequencer) // upStream's aspect
      }
    })
    hangingConnections --= hangingConnectionsToDel

    application.connections.foreach(c =>
      if (sequencers.contains(c.to.stream.path)) {
        logger.debug(s"${c.to.stream.path} subscribes to ${c.from.path} ")
        val dstSequencer = getSequencer(c.to.stream).get
        dstSequencer.subscribe(c.from)
        addSubscriber(c.from, dstSequencer)
      } else {
        hangingConnections += c
      }
    )

  def addSubscriber(from: AtomicStreamRefKind[_], to: Recvable): Unit = {
    getWorkflow(from) match {
      case Some(wf) => wf.subscribedBy(to)
      case None =>
        getSequencer(from) match {
          case Some(seq) => seq.subscribedBy(to)
          case None =>
            getGenerator(from) match {
              case Some(gen) => gen.subscribedBy(to)
              case None => throw new Exception("Unknown consumer: " + from.path)
            }
        }
    }
  }
