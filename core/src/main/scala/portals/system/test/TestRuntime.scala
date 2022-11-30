package portals.system.test

import scala.util.Random

import portals.*

/** Internal API. Holds runtime information of the executed applications. */
private[portals] class TestRuntimeContext():
  private var _streams: Map[String, TestStream] = Map.empty
  private var _portals: Map[String, TestPortal] = Map.empty
  private var _workflows: Map[String, TestWorkflow] = Map.empty
  private var _sequencers: Map[String, TestSequencer] = Map.empty
  private var _generators: Map[String, TestGenerator] = Map.empty
  private var _connections: Map[String, TestConnection] = Map.empty
  def streams: Map[String, TestStream] = _streams
  def portals: Map[String, TestPortal] = _portals
  def workflows: Map[String, TestWorkflow] = _workflows
  def sequencers: Map[String, TestSequencer] = _sequencers
  def generators: Map[String, TestGenerator] = _generators
  def connections: Map[String, TestConnection] = _connections
  def addStream(stream: AtomicStream[_]): Unit = _streams += stream.path -> TestStream(stream)(using this)
  def addPortal(portal: AtomicPortal[_, _]): Unit = _portals += portal.path -> TestPortal(portal)(using this)
  def addWorkflow(wf: Workflow[_, _]): Unit = _workflows += wf.path -> TestWorkflow(wf)(using this)
  def addSequencer(seqr: AtomicSequencer[_]): Unit = _sequencers += seqr.path -> TestSequencer(seqr)(using this)
  def addGenerator(genr: AtomicGenerator[_]): Unit = _generators += genr.path -> TestGenerator(genr)(using this)
  def addConnection(conn: AtomicConnection[_]): Unit = _connections += conn.path -> TestConnection(conn)(using this)

/** Internal API. Tracks the progress for a path with respect to other streams. */
private[portals] class TestProgressTracker:
  // progress tracker for each Path;
  // for a Path (String) this gives the progress w.r.t. all input dependencies (Map[String, Long])
  private var progress: Map[String, Map[String, Long]] = Map.empty

  /** Set the progress of path and dependency to index. */
  def setProgress(path: String, dependency: String, idx: Long): Unit =
    progress +=
      path ->
        (progress(path) + (dependency -> idx))

  /** Increments the progress of path w.r.t. dependency by 1. */
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

  /** Get all outgoing edges to a graph node with the name 'path'. */
  def getOutputs(path: String): Option[Set[String]] = Some(_edges.filter(_._1 == path).map(_._2))

/** Internal API. Tracks all streams of all applications.
  *
  * The stream tracker is used to track the progress of the streams, i.e. what range of indices of the stream that can
  * be read. The smallest index may be incremented due to garbage collection over time.
  */
private[portals] class TestStreamTracker:
  /** Maps the progress of a path (String) to a pair [from, to], the range is inclusive and means that all indices
    * starting from 'from' until (including) 'to' can be read.
    */
  private var _progress: Map[String, (Long, Long)] = Map.empty

  /** Initialize a new stream by settings its progress to <0, -1>, that is it is empty for now. */
  def initStream(stream: String): Unit = _progress += stream -> (0, -1)

  /** Set the progress of a stream to <from, to>, for which the range is inclusive. Use this with care, use
    * incrementProgress instead where possible.
    */
  def setProgress(stream: String, from: Long, to: Long): Unit = _progress += stream -> (from, to)

  /** Increments the progress of a stream by 1. */
  def incrementProgress(stream: String): Unit = _progress += stream -> (_progress(stream)._1, _progress(stream)._2 + 1)

  /** Returns the progress of a stream as an optional range <From, To>, for which the range is inclusive. */
  def getProgress(stream: String): Option[(Long, Long)] = _progress.get(stream)

class TestRuntime(val seed: Option[Int] = None):
  private val rctx = new TestRuntimeContext()
  private val progressTracker = TestProgressTracker()
  private val streamTracker = TestStreamTracker()
  private val graphTracker = TestGraphTracker()
  private val rnd = seed.map(new Random(_)).getOrElse(new Random())
  private var portalTracker: Map[String, String] = Map.empty // from portal to wf

  /** The current step number of the execution. */
  private var _stepN: Long = 0
  private def stepN: Long = { _stepN += 1; _stepN }

  private inline val GC_INTERVAL = 128 // GC Step Interval

  /** Launch an application. */
  def launch(application: Application): Unit =
    // launch streams
    application.streams.foreach { stream =>
      rctx.addStream(stream)
      streamTracker.initStream(stream.path)
    }

    // launch portals
    application.portals.foreach { portal =>
      rctx.addPortal(portal)
    }

    // launch workflows
    application.workflows.foreach { wf =>
      rctx.addWorkflow(wf)
      graphTracker.addEdge(wf.consumes.path, wf.path)
      graphTracker.addEdge(wf.path, wf.stream.path)
      progressTracker.initProgress(wf.path, wf.consumes.path)

      // add portal dependencies
      wf.tasks.foreach((name, task) =>
        task match
          case atask @ AskerTask(_) => List.empty
          case rtask @ ReplierTask(_, _) =>
            rtask.portals.toList
            rctx.portals(rtask.portals.head.path).replier = wf.path
            rctx.portals(rtask.portals.head.path).replierTask = name
          case _ => List.empty
      )
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
      progressTracker.initProgress(conn.to.path, conn.from.path)
    }

    // launch generators
    application.generators.foreach { genr =>
      rctx.addGenerator(genr)
      graphTracker.addEdge(genr.path, genr.stream.path)
    }

  /** Perform GC on the runtime objects. */
  private def garbageCollection(): Unit =
    ////////////////////////////////////////////////////////////////////////////
    // 1. Cleanup streams, compute min progress of stream dependents, and adjust accoringly
    ////////////////////////////////////////////////////////////////////////////
    rctx.streams.foreach { (streamName, stream) =>
      val streamProgress = streamTracker.getProgress(streamName).get
      val outputs = graphTracker.getOutputs(streamName).get
      val minprogress = outputs
        .map { outpt => progressTracker.getProgress(outpt, streamName).get }
        .minOption
        .getOrElse(-1L) // TODO: this could be set to streamProgress._2 instead if no subscribers exist
      if minprogress > streamProgress._1 + GC_INTERVAL then
        stream.prune(minprogress)
        streamTracker.setProgress(streamName, minprogress, streamProgress._2)
    }

  private def hasInput(path: String, dependency: String): Boolean =
    val progress = progressTracker.getProgress(path, dependency).get
    val streamProgress = streamTracker.getProgress(dependency).get._2
    progress < streamProgress

  private def hasInput(path: String): Boolean =
    val inputs = graphTracker.getInputs(path).get
    inputs.exists(inpt => hasInput(path, inpt))

  private inline def randomSelection[T](from: Map[String, T], predicate: (String, T) => Boolean): Option[(String, T)] =
    if from.size == 0 then None
    else
      val nxt = rnd.nextInt(from.size)
      from.drop(nxt).find((s, t) => predicate(s, t)) match
        case x @ Some(v) => x
        case None =>
          from.take(nxt).find((s, t) => predicate(s, t)) match
            case x @ Some(v) => x
            case None => None

  private def choosePortal(): Option[(String, TestPortal)] =
    randomSelection(rctx.portals, (path, portal) => !portal.isEmpty)

  private def chooseWorkflow(): Option[(String, TestWorkflow)] =
    randomSelection(rctx.workflows, (path, wf) => hasInput(path))

  private def chooseSequencer(): Option[(String, TestSequencer)] =
    randomSelection(rctx.sequencers, (path, seqr) => hasInput(path))

  private def chooseGenerator(): Option[(String, TestGenerator)] =
    randomSelection(rctx.generators, (path, genr) => genr.generator.generator.hasNext())

  private def distributeAtoms(listOfAtoms: List[TestAtom]): Unit =
    listOfAtoms.foreach {
      case ta @ TestAtomBatch(path, list) =>
        rctx.streams(path).enqueue(ta)
        streamTracker.incrementProgress(path)
      case tpa @ TestAskBatch(meta, _) =>
        rctx.portals(meta.portal).enqueue(tpa)
      case tpr @ TestRepBatch(meta, _) =>
        rctx.portals(meta.portal).enqueue(tpr)
    }

  private def stepPortal(path: String, portal: TestPortal): Unit =
    // dequeue the head event of the Portal
    portal.dequeue().get match
      // 1) if it is a TestAskBatch, then execute the replying workflow
      case tpa @ TestAskBatch(meta, _) =>
        val wf = rctx.workflows(portal.replier)
        val outputAtoms = wf.process(tpa)
        distributeAtoms(outputAtoms)
      // 2) if it is a TestRepBatch, then execute the asking workflow
      case tpr @ TestRepBatch(meta, _) =>
        val wf = rctx.workflows(meta.askingWF)
        val outputAtoms = wf.process(tpr)
        distributeAtoms(outputAtoms)
      case _ => ??? // should not happen

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
    choosePortal() match
      case Some(path, portal) => stepPortal(path, portal)
      case None =>
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
    if stepN % GC_INTERVAL == 0 then garbageCollection()

  /** Takes steps until it cannot take more steps. */
  def stepUntilComplete(): Unit =
    while canStep() do step()

  /** If the runtime can take another step, returns true if it can process something. It returns false if it has
    * finished processing, i.e. all atomic streams have been read.
    */
  def canStep(): Boolean =
    // use || so that we do not evaluate the other options unnecessarily
    choosePortal().isDefined
      || chooseWorkflow().isDefined
      || chooseSequencer().isDefined
      || chooseGenerator().isDefined

  /** Terminate the runtime. */
  def shutdown(): Unit = () // do nothing :)
