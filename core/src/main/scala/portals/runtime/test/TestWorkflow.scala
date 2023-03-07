package portals

import portals.*
import portals.application.*
import portals.application.task.*
import portals.runtime.executor.TaskExecutorImpl

// TODO: remove this
object TestWorkflow:
  sealed trait ExecutionInfo
  case class AskInfo(portal: String, askingWF: String) extends ExecutionInfo
  case class ReplyInfo(portal: String, askingWF: String) extends ExecutionInfo
  case object EventInfo extends ExecutionInfo

// TODO: remove this
private class TestWorkflowContext:
  import TestWorkflow.*
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
  private val runner = TaskExecutorImpl()
  // topographically sorted according to connections
  private val sortedTasks = wf.tasks.toList.sortWith((t1, t2) => getOrdinal(t1._1, wf) < getOrdinal(t2._1, wf))
  private val initializedTasks = sortedTasks.map { (name, task) =>
    (name, TaskExecution.prepareTask(task, runner.ctx.asInstanceOf))
  }
  // clear any strange side-effects that happened during initialization
  runner.oc.clear(); runner.oc.clearAsks(); runner.oc.clearReps()

  /** Processes the atom, and produces a new list of atoms.
    *
    * The produced list of atoms may either be a regular atom for an output
    * atomic stream, or an atom for a portal. See the `TestAtom` trait for the
    * distinction.
    *
    * @param atom
    *   atom to be processed
    * @return
    *   list of produced atoms
    */
  def process(atom: TestAtom): List[TestAtom] =
    atom match
      case x @ TestAtomBatch(_, _) => processAtomBatch(x)
      case x @ TestAskBatch(_, _) => processAskBatch(x)
      case x @ TestRepBatch(_, _) => processReplyBatch(x)

  /** Internal API. Get the ordinal of a path in a workflow.
    *
    * @param path
    *   path to get the ordinal of in the workflow
    * @param workflow
    *   workflow to get the ordinal of the path in
    * @return
    *   ordinal of the path in the workflow
    */
  private def getOrdinal(path: String, workflow: Workflow[_, _]): Int =
    val idx = workflow.connections.reverse.map(_._1).indexOf(path)
    if idx == -1 then workflow.connections.reverse.size else idx

  /** Internal API. Process a task with inputs on the previous outputs. */
  private def processTask(
      path: String,
      task: GenericTask[_, _, _, _],
      inputs: List[String],
      outputs: Map[String, TestAtomBatch[_]],
  ): TestAtomBatch[_] = {

    runner.setup(path, task.asInstanceOf)
    runner.run_batch(
      runner.clean_events(
        inputs.filter(input => outputs.contains(input)).map(input => outputs(input)).flatMap(x => x.list)
      )
    )

    val output = runner.oc.getOutput()
    runner.oc.clear()
    TestAtomBatch(null, output)
  }

  /** Internal API. Process a sink with inputs on the previous outputs. */
  private def processSink(
      path: String,
      inputs: List[String],
      outputs: Map[String, TestAtomBatch[_]]
  ): TestAtomBatch[_] = {

    // all the outputs of the sink
    val allOutputs = inputs
      .map(x => outputs.get(x))
      .filter(_.isDefined)
      .flatMap(x => x.get.list)

    // cleaned outputs, with duplicate Atom markers removed, etc.
    val cleanedAllOutputs = runner.clean_events(allOutputs)

    TestAtomBatch(wf.stream.path, cleanedAllOutputs)
  }

  /** Internal API. Processes the Workflow on some events.
    *
    * The provided _outputs parameter provides a mapping of task-names to their
    * output. The tasks in the workflow are then executed according to
    * topographical order to process their inputs. An example use of this
    * function is to provide the workflow input as a mapping from the source
    * name to the workflow input. This will in turn process the input on the
    * workflow, and produce a list of the produced atoms, typically this will be
    * a single TestAtomBatch. If there are asker tasks, or replyer tasks, then
    * it may also produce several AskBatches and RepBatches.
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
        runner.oc
          .getAskOutput()
          // here we could also omit grouping by askingTask...
          .groupBy { case Ask(_, portalMeta, _) => (portalMeta.portal) }
          .map { (k, v) => TestAskBatch(PortalBatchMeta(k, wf.path), v) }
          .toList
      val repoutputs =
        runner.oc
          .getRepOutput()
          .groupBy { case Reply(_, portalMeta, _) => (portalMeta.portal) }
          .map { (k, v) => TestRepBatch(PortalBatchMeta(k, wctx.getAskingWF()), v) }
          .toList
      askoutputs ::: repoutputs
    }

    ////////////////////////////////////////////////////////////////////////////
    // 4. Cleanup and Return
    ////////////////////////////////////////////////////////////////////////////
    runner.oc.clear()
    runner.oc.clearAsks()
    runner.oc.clearReps()
    outputs(wf.sink) :: askAndReplyOutputs
  end processAtomHelper

  /** Internal API. Process the ReplierTask on a batch of asks. */
  private def processReplierTask(
      path: String,
      task: ReplierTask[_, _, _, _],
      input: TestAskBatch[_]
  ): TestAtomBatch[_] = {
    input.list.foreach { event =>
      event match
        case Ask(key, meta, e) =>
          runner.ctx.state.key = key
          runner.ctx.key = key
          runner.ctx.id = meta.id
          runner.ctx.asker = meta.askingTask
          runner.ctx.portal = meta.portal
          runner.ctx.path = path
          runner.ctx.task = task.asInstanceOf
          runner.ctx.askerKey = meta.askingKey
          task.f2(runner.ctx.asInstanceOf)(e.asInstanceOf)
        case _ => ???
    }
    val output = runner.oc.getOutput()
    runner.oc.clear()
    TestAtomBatch(null, output)
  }

  /** Internal API. Process a TestAskBatch. */
  private def processAskBatch(atom: TestAskBatch[_]): List[TestAtom] = {
    wctx.info = AskInfo(atom.meta.portal, atom.meta.askingWF)
    val taskName = rctx.portals(atom.meta.portal).replierTask
    val task = initializedTasks.toMap.get(taskName).get
    val outputs1 = processReplierTask(taskName, task.asInstanceOf, atom)
    val outputs2 = processAtomHelper(Map(taskName -> outputs1))
    wctx.info = null
    outputs2
  }

  /** Internal API. Process a TestReplyBatch. Will process matched
    * continuations.
    */
  private def processAskerTask(path: String, atom: TestRepBatch[_]): TestAtomBatch[_] = {
    atom.list.foreach { event =>
      event match
        case Reply(key, meta, e) =>
          runner.ctx.state.key = key
          runner.ctx.key = key
          runner.ctx.path = path
          runner.ctx.state.path = path
          runner.ctx.task = null
          runner.run_and_cleanup_reply(meta.id, e)(using runner.ctx)
        case _ => ??? // NOPE
    }

    val output = runner.oc.getOutput()
    runner.oc.clear()
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
