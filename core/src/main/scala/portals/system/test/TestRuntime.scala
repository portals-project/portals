package portals.system.test

import portals.*

/** Internal API. Holds runtime information of the executed applications. */
private[portals] class TestRuntimeContext():
  private var _streams: Map[String, TestStream] = Map.empty
  private var _workflows: Map[String, TestWorkflow] = Map.empty
  private var _sequencers: Map[String, TestSequencer] = Map.empty
  private var _generators: Map[String, TestGenerator] = Map.empty
  private var _connections: Map[String, TestConnection] = Map.empty
  def streams: Map[String, TestStream] = _streams
  def workflows: Map[String, TestWorkflow] = _workflows
  def sequencers: Map[String, TestSequencer] = _sequencers
  def generators: Map[String, TestGenerator] = _generators
  def connections: Map[String, TestConnection] = _connections
  def addStream(stream: AtomicStream[_]): Unit = _streams += stream.path -> TestStream(stream)(using this)
  def addWorkflow(wf: Workflow[_, _]): Unit = _workflows += wf.path -> TestWorkflow(wf)(using this)
  def addSequencer(seqr: AtomicSequencer[_]): Unit = _sequencers += seqr.path -> TestSequencer(seqr)(using this)
  def addGenerator(genr: AtomicGenerator[_]): Unit = _generators += genr.path -> TestGenerator(genr)(using this)
  def addConnection(conn: AtomicConnection[_]): Unit = _connections += conn.path -> TestConnection(conn)(using this)

/** Internal API. Tracks the progress for a path with respect to other streams. */
class TestProgressTracker:
  // progress tracker for each Path;
  // for a Path (String) this gives the progress w.r.t. all input dependencies (Map[String, Long])
  private var progress: Map[String, Map[String, Long]] = Map.empty

  /** Set the progress of path and dependency to index. */
  def setProgress(path: String, dependency: String, idx: Long): Unit =
    progress +=
      path ->
        (progress(path) + (dependency -> idx))

  /** Increments the progress of path w.r.t. dependency by 1. If the dependency doesn't exist yet, then we set the new
    * index to 0.
    */
  def incrementProgress(path: String, dependency: String): Unit =
    progress +=
      path ->
        (progress(path) + (dependency -> (progress(path)(dependency) + 1)))

  /** Initialize the progress tracker for a certain path and dependency to -1. */
  def initProgress(path: String, dependency: String): Unit =
    progress += path -> (progress.getOrElse(path, Map.empty) + (dependency -> -1L))

  /** Get the current progress of the path and dependency. */
  def getProgress(path: String, dependency: String): Option[Long] =
    progress.get(path).flatMap(deps => deps.get(dependency))

  /** Get the current progress of the path. */
  def getProgress(path: String): Option[Map[String, Long]] =
    progress.get(path)

/** Internal API. Tracks the graph which is spanned by all applications in Portals. */
class TestGraphTracker:
  /** Set of all pairs <from, to> edges. */
  private var _edges: Set[(String, String)] = Set.empty

  /** Add an edge <from, to> to the graph. */
  def addEdge(from: String, to: String): Unit = _edges += (from, to)

  /** Get all incoming edges to a graph node with the name 'path'. */
  def getInputs(path: String): Option[Set[String]] = Some(_edges.filter(_._2 == path).map(_._1))

/** Internal API. Tracks all streams of all applications. */
class TestStreamTracker:
  /** Maps the progress of a path (String) to a pair [from, to], the range is inclusive and means that all indices
    * starting from 'from' until (including) 'to' can be read.
    */
  private var _progress: Map[String, (Long, Long)] = Map.empty

  def initStream(stream: String): Unit = _progress += stream -> (0, -1)

  def setProgress(stream: String, from: Long, to: Long): Unit = _progress += stream -> (from, to)

  def incrementProgress(stream: String): Unit = _progress += stream -> (_progress(stream)._1, _progress(stream)._2 + 1)

  def getProgress(stream: String): Option[(Long, Long)] = _progress.get(stream)

class TestRuntime:
  private val rctx = new TestRuntimeContext()
  private val progressTracker = TestProgressTracker()
  private val streamTracker = TestStreamTracker()
  private val graphTracker = TestGraphTracker()

  /** Launch an application. */
  def launch(application: Application): Unit =
    // launch streams
    application.streams.foreach { stream =>
      rctx.addStream(stream)
      streamTracker.initStream(stream.path)
    }

    // launch workflows
    application.workflows.foreach { wf =>
      rctx.addWorkflow(wf)
      graphTracker.addEdge(wf.consumes.path, wf.path)
      graphTracker.addEdge(wf.path, wf.stream.path)
      progressTracker.initProgress(wf.path, wf.consumes.path)
    }

    // launch sequencers
    application.sequencers.foreach { seqr =>
      rctx.addSequencer(seqr)
      graphTracker.addEdge(seqr.path, seqr.stream.path)
    }

    // launch connections
    application.connections.foreach { conn =>
      rctx.addConnection(conn)
      graphTracker.addEdge(conn.from.path, conn.to.path)
    }

    // launch generators
    application.generators.foreach { genr =>
      rctx.addGenerator(genr)
      graphTracker.addEdge(genr.path, genr.stream.path)
    }

  private def hasInput(path: String, dependency: String): Boolean =
    val progress = progressTracker.getProgress(path, dependency).get
    val streamProgress = streamTracker.getProgress(dependency).get._2
    progress < streamProgress

  private def hasInput(to: String): Boolean =
    val inputs = graphTracker.getInputs(to).get
    inputs.exists(inpt => hasInput(to, inpt))

  private def chooseWorkflow(): Option[(String, TestWorkflow)] =
    rctx.workflows.find((path, wf) => hasInput(path))

  private def chooseSequencer(): Option[(String, TestSequencer)] =
    rctx.sequencers.find((path, seqr) => hasInput(path))

  private def chooseGenerator(): Option[(String, TestGenerator)] =
    rctx.generators.find((path, genr) => genr.generator.generator.hasNext())

  private def distributeAtoms(listOfAtoms: List[TestAtom]): Unit =
    listOfAtoms.foreach { case ta @ TestAtomBatch(path, list) =>
      rctx.streams(path).enqueue(ta)
      streamTracker.incrementProgress(path)
    }

  private def stepWorkflow(path: String, wf: TestWorkflow): Unit =
    val from = graphTracker.getInputs(path).get.find(from => hasInput(path, from)).get
    val idx = progressTracker.getProgress(path, from).get
    val inputAtom = rctx.streams(from).read(idx)
    val outputAtoms = wf.process(inputAtom)
    distributeAtoms(outputAtoms)
    progressTracker.incrementProgress(path, from)

  private def stepSequencer(path: String, seqr: TestSequencer): Unit =
    val from = graphTracker.getInputs(path).get.find(from => hasInput(path, from)).get
    val idx = progressTracker.getProgress(path, from).get
    val inputAtom = rctx.streams(from).read(idx)
    val outputAtoms = seqr.process(inputAtom)
    distributeAtoms(outputAtoms)
    progressTracker.incrementProgress(path, from)

  private def stepGenerator(path: String, genr: TestGenerator): Unit =
    val outputAtoms = genr.process()
    distributeAtoms(outputAtoms)

  /** Take a step. This will cause one of the processing entities (Workflows, Sequencers, etc.) to process one atom and
    * produce one (or more) atoms. Throws an exception if it cannot take a step.
    */
  def step(): Unit =
    chooseWorkflow() match
      case Some(path, wf) => stepWorkflow(path, wf)
      case None =>
        chooseSequencer() match
          case Some(path, seqr) => stepSequencer(path, seqr)
          case None =>
            chooseGenerator() match
              case Some(path, genr) =>
                stepGenerator(path, genr)
              case None => ???

  /** Takes steps until it cannot take more steps. */
  def stepUntilComplete(): Unit =
    while canStep() do step()

  /** If the runtime can take another step, returns true if it can process something. It returns false if it has
    * finished processing, i.e. all atomic streams have been read.
    */
  def canStep(): Boolean =
    chooseWorkflow().isDefined
      || chooseSequencer().isDefined
      || chooseGenerator().isDefined

  /** Terminate the runtime. */
  def shutdown(): Unit = () // do nothing :)
