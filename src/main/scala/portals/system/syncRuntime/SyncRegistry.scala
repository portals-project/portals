package portals

// TODO move to a separate file
class SyncRegistry extends GlobalRegistry:
  var sequencers = Map[String, RuntimeSequencer[_]]()
  var workflows = Map[String, RuntimeWorkflow]()
  var generators = Map[String, RuntimeGenerator[_]]()

//   var executables = List[Executable]()

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

  def getSequencer[T](stream: AtomicStreamRefKind[_]): Option[RuntimeSequencer[_]] = {
    sequencers.get(stream.path)
  }

  def getWorkflow[T](stream: AtomicStreamRefKind[_]): Option[RuntimeWorkflow] = {
    workflows.get(stream.path)
  }

  def getGenerator[T](stream: AtomicStreamRefKind[_]): Option[RuntimeGenerator[_]] = {
    generators.get(stream.path)
  }
