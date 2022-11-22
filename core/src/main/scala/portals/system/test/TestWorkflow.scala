package portals.system.test

import portals.*

/** Internal API. TaskCallback to collect submitted events as side effects. Works both for regular tasks and for
  * AskerTasks and ReplierTasks.
  */
private class CollectingTaskCallBack[T, U, Req, Rep] extends TaskCallback[T, U, Req, Rep]:
  //////////////////////////////////////////////////////////////////////////////
  // Task
  //////////////////////////////////////////////////////////////////////////////
  private var _output = List.empty[WrappedEvent[U]]
  def submit(event: WrappedEvent[U]): Unit = _output = event :: _output
  def getOutput(): List[WrappedEvent[U]] = _output.reverse
  def clear(): Unit = _output = List.empty

  //////////////////////////////////////////////////////////////////////////////
  // Asker Task
  //////////////////////////////////////////////////////////////////////////////
  private var _asks = List.empty[Ask[Req]]
  override def ask(
      portal: String,
      portalAsker: String,
      replier: String,
      asker: String,
      req: Req,
      key: Key[Int],
      id: Int
  ): Unit =
    _asks = Ask(key, portal, portalAsker, replier, asker, id, req) :: _asks
  def getAskOutput(): List[Ask[Req]] = _asks.reverse
  def clearAsks(): Unit = _asks = List.empty

  //////////////////////////////////////////////////////////////////////////////
  // Replier Task
  //////////////////////////////////////////////////////////////////////////////
  private var _reps = List.empty[Reply[Rep]]
  override def reply(
      r: Rep,
      portal: String,
      portalAsker: String,
      replier: String,
      asker: String,
      key: Key[Int],
      id: Int
  ): Unit = _reps = Reply(key, portal, portalAsker, replier, asker, id, r) :: _reps
  def getRepOutput(): List[Reply[Rep]] = _reps.reverse
  def clearReps(): Unit = _reps = List.empty
end CollectingTaskCallBack // class

/** Internal API. TestRuntime wrapper of a Workflow. */
private[portals] class TestWorkflow(wf: Workflow[_, _])(using rctx: TestRuntimeContext):
  private val tcb = CollectingTaskCallBack[Any, Any, Any, Any]()
  private val tctx = TaskContext[Any, Any]()
  val actx = AskerTaskContext.fromTaskContext(tctx, tcb)
  val rectx = ReplierTaskContext.fromTaskContext(tctx, tcb)
  tctx.cb = tcb

  //////////////////////////////////////////////////////////////////////////////
  // Initialize the workflow
  /////////////////////////////////////////////////////////////////////////////
  // Gets the ordinal of a path with respect to the topology of the graph.
  // To be used to sort the graph in topological order.
  private def getOrdinal(path: String): Int =
    val idx = wf.connections.reverse.map(_._1).indexOf(path)
    if idx == -1 then wf.connections.reverse.size else idx

  // topographically sorted according to connections
  private val sortedTasks = wf.tasks.toList.sortWith((t1, t2) => getOrdinal(t1._1) < getOrdinal(t2._1))
  // and initialized / prepared
  private val initializedTasks = sortedTasks.map { (name, task) =>
    (name, Tasks.prepareTask(task, tctx.asInstanceOf))
  }
  // clear any strange side-effects that happened during initialization
  tcb.clear(); tcb.clearAsks(); tcb.clearReps()

  /** Processes the atom, and produces a new list of atoms.
    *
    * The produced list of atoms may either be a regular atom for an output atomic stream, or an atom for a portal. See
    * the `TestAtom` trait for the distinction.
    */
  def process(atom: TestAtom): List[TestAtom] =
    atom match
      case x @ TestAtomBatch(_, _) => processAtomBatch(x)
      case x @ TestAskBatch(_, _, _, _) => processAskBatch(x)
      case x @ TestRepBatch(_, _, _, _) => processReplyBatch(x)

  /** Internal API. Process a task with inputs on the previous outputs. */
  private def processTask(
      path: String,
      task: Task[_, _],
      inputs: List[String],
      outputs: Map[String, TestAtomBatch[_]],
  ): TestAtomBatch[_] = {

    // if the task has sealed, errored, or processed a full atom.
    var seald = false
    var errord = false
    var atomd = false

    inputs.foreach { input =>
      if outputs.contains(input) then
        val atom = outputs.get(input).get
        atom.list.foreach { event =>
          event match
            case Event(key, e) =>
              task match {
                case _: AskerTask[_, _, _, _] =>
                  actx.key = key
                  actx.state.key = key
                  actx.path = path
                  task.onNext(using actx.asInstanceOf)(e.asInstanceOf)
                case _: Task[?, ?] =>
                  tctx.key = key
                  tctx.state.key = key
                  tctx.path = path
                  task.onNext(using tctx.asInstanceOf)(e.asInstanceOf)
              }
            case Atom =>
              // Here we don't simply emit the atom, as a task may have multiple
              // inputs which would then send one atom-marker each.
              // This is why we have this intermediate step here, same for seald.
              atomd = true
            case Seal =>
              seald = true
            case Error(t) =>
              errord = true
              task.onError(using tctx.asInstanceOf)(t)
            case _ => ???
        }
    }
    if errord then ??? // TODO: how to handle errors?
    else if seald then
      task.onComplete(using tctx.asInstanceOf)
      tcb.submit(Seal)
    else if atomd then
      task.onAtomComplete(using tctx.asInstanceOf)
      tcb.submit(Atom)

    val output = tcb.getOutput()
    tcb.clear()
    TestAtomBatch(null, output)
  }

  /** Internal API. Process a sink with inputs on the previous outputs. */
  private def processSink(
      path: String,
      inputs: List[String],
      outputs: Map[String, TestAtomBatch[_]]
  ): TestAtomBatch[_] = {
    // Processing a sink will concatenate all outputs, and deduplicate any Atom, Seal, Error events.

    var _output = List.empty[WrappedEvent[_]]
    var atomd = false
    var seald = false
    var errord = false

    inputs.foreach { input =>
      if outputs.contains(input) then
        val atom = outputs(input)
        atom.list.foreach { event =>
          event match
            case Event(key, e) => _output = Event(key, e) :: _output
            case Atom => atomd = true
            case Seal => seald = true
            case Error(t) => errord = true
            case _ => ???
        }
    }

    if errord then ??? // TODO: how to handle errors?
    else if seald then _output = Seal :: _output
    else if atomd then _output = Atom :: _output
    else Atom :: _output // if no events...

    TestAtomBatch(wf.stream.path, _output.reverse)
  }

  /** Internal API. Processes the Workflow on some events.
    *
    * The provided _outputs parameter provides a mapping of task-names to their output. The tasks in the workflow are
    * then executed according to topographical order to process their inputs. An example use of this function is to
    * provide the workflow input as a mapping from the source name to the workflow input. This will in turn process the
    * input on the workflow, and produce a list of the produced atoms, typically this will be a single TestAtomBatch. If
    * there are asker tasks, or replyer tasks, then it may also produce several AskBatches and RepBatches.
    */
  private def processAtomHelper(_outputs: Map[String, TestAtomBatch[_]]): List[TestAtom] =
    // A mapping from task/source/sink name to their output
    var outputs: Map[String, TestAtomBatch[_]] = _outputs

    ////////////////////////////////////////////////////////////////////////////
    // 1. Execute tasks in topographical order
    ////////////////////////////////////////////////////////////////////////////
    sortedTasks.foreach { (path, task) =>
      // all inputs to the task
      val inputs = wf.connections.filter((from, to) => to == path).map(_._1)
      // process the task, add the produce events to outputs
      val output = processTask(path, task, inputs, outputs)
      if output.list.nonEmpty then outputs += (path -> output)
    }

    ////////////////////////////////////////////////////////////////////////////
    // 2. Execute the sink.
    ////////////////////////////////////////////////////////////////////////////
    {
      val inputs = wf.connections.filter((from, to) => to == wf.sink).map(_._1)
      outputs += wf.sink -> processSink(wf.sink, inputs, outputs)
    }

    ////////////////////////////////////////////////////////////////////////////
    // 3. Collect AskBatches and RepBatches from the CallBack
    ////////////////////////////////////////////////////////////////////////////
    val askAndReplyOutputs = {
      val askoutputs =
        tcb
          .getAskOutput()
          .groupBy(e => (e.portal, e.replier))
          .map { (k, v) => TestAskBatch(k._1, wf.path, k._2, v) }
          .toList
      val repoutputs =
        tcb
          .getRepOutput()
          .groupBy(e => (e.portal, e.portalAsker))
          .map { (k, v) => TestRepBatch(k._1, k._2, wf.path, v) }
          .toList
      askoutputs ::: repoutputs
    }

    ////////////////////////////////////////////////////////////////////////////
    // 4. Cleanup and Return
    ////////////////////////////////////////////////////////////////////////////
    tcb.clear()
    tcb.clearAsks()
    tcb.clearReps()
    outputs(wf.sink) :: askAndReplyOutputs
  end processAtomHelper

  /** Internal API. Process the ReplierTask on a batch of asks. */
  private def processReplierTask(task: Task[_, _], input: TestAskBatch[_]): TestAtomBatch[_] = {
    input.list.foreach { event =>
      event match
        case Ask(key, portal, portalAsker, replier, asker, id, e) =>
          rectx.key = key
          rectx.state.key = key
          rectx.path = replier
          rectx.id = id
          rectx.asker = asker
          rectx.portal = portal
          rectx.portalAsker = portalAsker
          task.asInstanceOf[ReplierTask[_, _, _, _]].f2(rectx.asInstanceOf)(e.asInstanceOf)
        case _ => ???
    }
    val output = tcb.getOutput()
    tcb.clear()
    TestAtomBatch(null, output)
  }

  /** Internal API. Process a TestAskBatch. */
  private def processAskBatch(atom: TestAskBatch[_]): List[TestAtom] = {
    // process the AskBatch
    val outputs1 = atom.list
      .groupBy(_.asInstanceOf[Ask[_]].replier)
      .map { (replier, batch) =>
        val task = initializedTasks.toMap.get(replier).get // TODO: optimize :))))
        (replier, processReplierTask(task, TestAskBatch(replier, atom.asker, replier, batch)))
      }
      .toMap

    val outputs2 = processAtomHelper(outputs1)
    outputs2
  }

  /** Internal API. Process a TestReplyBatch. Will process matched continuations. */
  private def processAskerTask(path: String, atom: TestRepBatch[_]): TestAtomBatch[_] = {
    atom.list.foreach { event =>
      event match
        case Reply(key, portal, portalAsker, replier, asker, id, e) =>
          actx.key = key
          actx.state.key = key
          actx.path = path
          AskerTask.run_and_cleanup_reply(id, e)(using actx)
        case _ => ??? // NOPE
    }

    val output = tcb.getOutput()
    tcb.clear()
    TestAtomBatch(null, output)
  }

  /** Internal API. Process a TestReplyBatch. */
  private def processReplyBatch(atom: TestRepBatch[_]): List[TestAtom] = {
    val outputs = atom.list
      .groupBy(_.asInstanceOf[Reply[_]].asker)
      .map { (asker, batch) =>
        (asker, processAskerTask(asker, TestRepBatch(atom.portal, atom.asker, atom.replier, batch)))
      }
      .toMap
    processAtomHelper(outputs)
  }

  /** Internal API. Processes a TestAtomBatch on the workflow. */
  private def processAtomBatch(atom: TestAtomBatch[_]): List[TestAtom] =
    var outputs: Map[String, TestAtomBatch[_]] = Map.empty

    // set the source output to be the input atom
    outputs += wf.source -> atom

    // process the atom batch on the outputs with the source set to the input
    processAtomHelper(outputs)
  end processAtomBatch
