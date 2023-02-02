package portals

import portals.*

object TestWorkflow:
  enum ExecutionMode:
    case EventMode, AskMode, ReplyMode

  sealed trait ExecutionInfo
  case class AskInfo(portal: String, askingWF: String) extends ExecutionInfo
  case class ReplyInfo(portal: String, askingWF: String) extends ExecutionInfo
  case object EventInfo extends ExecutionInfo

private class TestWorkflowContext:
  import TestWorkflow.*
  var mode: ExecutionMode = _
  var info: ExecutionInfo = _

  def getAskingWF(): String = info match
    case AskInfo(_, x) => x
    case ReplyInfo(_, x) => x
    case _ => ???

  def getPortal(): String = info match
    case AskInfo(x, _) => x
    case ReplyInfo(x, _) => x
    case _ => ???

/** Internal API. TestRuntime wrapper of a Workflow. */
private[portals] class TestWorkflow(wf: Workflow[_, _])(using rctx: TestRuntimeContext):
  import TestWorkflow.*
  private val wctx = new TestWorkflowContext()
  private val tcb = OutputCollectorImpl[Any, Any, Any, Any]()
  private val tctx = TaskContextImpl[Any, Any, Any, Any]()
  // val actx = AskerTaskContext.fromTaskContext(tctx, tcb)
  // val rectx = ReplierTaskContext.fromTaskContext(tctx, tcb)
  tctx.outputCollector = tcb

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
    (name, TaskExecution.prepareTask(task, tctx.asInstanceOf))
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
      case x @ TestAskBatch(_, _) => processAskBatch(x)
      case x @ TestRepBatch(_, _) => processReplyBatch(x)

  /** Internal API. Process a task with inputs on the previous outputs. */
  private def processTask(
      path: String,
      task: GenericTask[_, _, _, _],
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
                  tctx.state.key = key
                  tctx.key = key
                  tctx.path = path
                  tctx.task = task.asInstanceOf
                  task.asInstanceOf[GenericTask[Any, Any, Any, Any]].onNext(using tctx)(e)
                case _: GenericTask[_, _, _, _] =>
                  tctx.state.key = key
                  tctx.key = key
                  tctx.path = path
                  tctx.task = task.asInstanceOf
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
    if errord then ???
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
          // here we could also omit grouping by askingTask...
          .groupBy { case Ask(_, portalMeta, _) => (portalMeta.portal) }
          .map { (k, v) => TestAskBatch(PortalBatchMeta(k, wf.path), v) }
          .toList
      val repoutputs =
        tcb
          .getRepOutput()
          .groupBy { case Reply(_, portalMeta, _) => (portalMeta.portal) }
          .map { (k, v) => TestRepBatch(PortalBatchMeta(k, wctx.getAskingWF()), v) }
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
  private def processReplierTask(task: ReplierTask[_, _, _, _], input: TestAskBatch[_]): TestAtomBatch[_] = {
    input.list.foreach { event =>
      event match
        case Ask(key, meta, e) =>
          tctx.state.key = key
          tctx.key = key
          tctx.id = meta.id
          tctx.asker = meta.askingTask
          tctx.portal = meta.portal
          tctx.task = task.asInstanceOf
          task.f2(tctx.asInstanceOf)(e.asInstanceOf)
        case _ => ???
    }
    val output = tcb.getOutput()
    tcb.clear()
    TestAtomBatch(null, output)
  }

  /** Internal API. Process a TestAskBatch. */
  private def processAskBatch(atom: TestAskBatch[_]): List[TestAtom] = {
    wctx.info = AskInfo(atom.meta.portal, atom.meta.askingWF)
    val taskName = rctx.portals(atom.meta.portal).replierTask
    val task = initializedTasks.toMap.get(taskName).get
    val outputs1 = processReplierTask(task.asInstanceOf, atom)
    val outputs2 = processAtomHelper(Map(taskName -> outputs1))
    wctx.info = null
    outputs2
  }

  /** Internal API. Process a TestReplyBatch. Will process matched continuations. */
  private def processAskerTask(path: String, atom: TestRepBatch[_]): TestAtomBatch[_] = {
    atom.list.foreach { event =>
      event match
        case Reply(key, meta, e) =>
          tctx.state.key = key
          tctx.key = key
          tctx.path = path
          tctx.task = null
          TaskExecution.run_and_cleanup_reply(meta.id, e)(using tctx)
        case _ => ??? // NOPE
    }

    val output = tcb.getOutput()
    tcb.clear()
    TestAtomBatch(null, output)
  }

  /** Internal API. Process a TestReplyBatch. */
  private def processReplyBatch(atom: TestRepBatch[_]): List[TestAtom] = {
    wctx.info = ReplyInfo(atom.meta.portal, atom.meta.askingWF)
    val outputs = atom.list
      .groupBy(_.asInstanceOf[Reply[_]].meta.askingTask)
      .map { (asker, batch) =>
        (asker, processAskerTask(asker, TestRepBatch(atom.meta, batch)))
      }
      .toMap
    wctx.info = null
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
