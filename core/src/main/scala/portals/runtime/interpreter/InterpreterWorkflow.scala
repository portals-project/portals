package portals.runtime.interpreter

import portals.application.*
import portals.application.task.*
import portals.runtime.executor.TaskExecutorImpl
import portals.runtime.interpreter.InterpreterEvents.*
import portals.runtime.interpreter.InterpreterRuntimeContext
import portals.runtime.WrappedEvents.*

/** Internal API. TestRuntime wrapper of a Workflow.
  *
  * @param wf
  *   workflow to be wrapped
  * @param rctx
  *   runtime context
  */
private[portals] class InterpreterWorkflow(wf: Workflow[_, _])(using rctx: InterpreterRuntimeContext):

  // init
  private val runner = TaskExecutorImpl()

  // topographically sorted according to connections
  private val sortedTasks = wf.tasks.toList.sortWith((t1, t2) => getOrdinal(t1._1, wf) < getOrdinal(t2._1, wf))
  private val initializedTasks = sortedTasks.map { (name, task) =>
    (name, runner.prepareTask(task))
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
  def process(atom: InterpreterAtom): List[InterpreterAtom] =
    atom match
      case x @ InterpreterAtomBatch(_, _) => processAtomBatch(x)
      case x @ InterpreterAskBatch(_, _) => processAskBatch(x)
      case x @ InterpreterRepBatch(_, _) => processReplyBatch(x)
  end process

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
  end getOrdinal

  /** Internal API. Processes a InterpreterAtomBatch on the workflow.
    *
    * @param atom
    *   atom batch to be processed
    * @return
    *   list of produced atoms
    */
  private def processAtomBatch(atom: InterpreterAtomBatch[_]): List[InterpreterAtom] =
    // set the source output to be the input atom
    val outputs = Map[String, InterpreterAtomBatch[_]](wf.source -> atom)

    // process the atom batch on the outputs with the source set to the input
    processAtomBatchHelper(outputs)
  end processAtomBatch

  /** Internal API. Process a TestAskBatch.
    *
    * @param atom
    *   ask batch to be processed
    * @return
    *   list of produced atoms
    */
  private def processAskBatch(atom: InterpreterAskBatch[_]): List[InterpreterAtom] = {
    // setup info
    val taskName = rctx.portals(atom.meta.portal).replierTask
    val task = initializedTasks.toMap.get(taskName).get.asInstanceOf[ReplierTaskKind[_, _, _, _]]

    // execute ask batch
    val outputs1 = processReplierTask(taskName, task, atom)

    // execute resulting events
    val outputs2 = processAtomBatchHelper(Map(taskName -> outputs1))

    // reset

    // return
    outputs2
  }
  end processAskBatch

  /** Internal API. Process a TestReplyBatch.
    *
    * @param atom
    *   reply batch to be processed
    * @return
    *   list of produced atoms
    */
  private def processReplyBatch(atom: InterpreterRepBatch[_]): List[InterpreterAtom] =
    // setup info

    // execute reply batch
    val outputs1 = atom.list
      .groupBy(_.asInstanceOf[Reply[_]].meta.askingTask)
      .map { (asker, batch) =>
        (asker, processAskerTask(asker, InterpreterRepBatch(atom.meta, batch)))
      }
      .toMap

    // reset

    // execute resulting events
    val outputs2 = processAtomBatchHelper(outputs1)

    // return
    outputs2
  end processReplyBatch

  /** Internal API. Process a task with inputs on the previous outputs.
    *
    * Ask and Reply events are captured by the output collector `oc`, and not
    * returned here.
    *
    * @param path
    *   path to the task
    * @param task
    *   task to be processed
    * @param inputs
    *   list of inputs (as names, Strings) of the workflow to be processed
    * @param outputs
    *   map of previous outputs, which will be used as inputs to this task
    */
  private def processTask(
      path: String,
      task: GenericTask[_, _, _, _],
      inputs: List[String],
      outputs: Map[String, InterpreterAtomBatch[_]],
  ): InterpreterAtomBatch[_] = {

    // setup runner for task
    runner.setup(path, wf.path, task.asInstanceOf)

    // execute task on batch
    runner.run_batch(
      runner.clean_events(
        inputs.filter(input => outputs.contains(input)).map(input => outputs(input)).flatMap(x => x.list)
      )
    )

    // get, clean, return outputs
    val allOutputs = runner.oc.getOutput()
    val cleanedAllOutputs = runner.clean_events(allOutputs)
    runner.oc.clear()
    InterpreterAtomBatch(null, cleanedAllOutputs)
  }

  /** Internal API. Process a sink with inputs on the previous outputs.
    *
    * Ask and Reply events are captured by the output collector `oc`, and not
    * returned here.
    *
    * @param path
    *   path of the sink
    * @param inputs
    *   list of inputs (from the workflow, Strings) to the sink
    * @param outputs
    *   map of the previous outputs, which will partly be used as inputs
    */
  private def processSink(
      path: String,
      inputs: List[String],
      outputs: Map[String, InterpreterAtomBatch[_]]
  ): InterpreterAtomBatch[_] = {

    // all the outputs of the sink
    val allOutputs = inputs
      .map(x => outputs.get(x))
      .filter(_.isDefined)
      .flatMap(x => x.get.list)

    // cleaned outputs, with duplicate Atom markers removed, etc.
    val cleanedAllOutputs = runner.clean_events(allOutputs)

    InterpreterAtomBatch(wf.stream.path, cleanedAllOutputs)
  }

  /** Internal API. Process the ReplierTask on a batch of asks.
    *
    * Ask and Reply events are captured by the runner output collector
    * `runner.oc`. Regular events are returned and cleared from the collector.
    *
    * @param path
    *   path of the replier task
    * @param task
    *   replier task to be processed
    * @param batch
    *   batch of asks to be processed
    * @return
    *   batch of regular events produced by the replier task
    */
  private def processReplierTask(
      path: String,
      task: ReplierTaskKind[_, _, _, _],
      batch: InterpreterAskBatch[_]
  ): InterpreterAtomBatch[_] =

    // setup runner for task
    runner.setup(path, wf.path, task.asInstanceOf)

    // execute replier task on ask batch
    runner.run_batch(batch.list)

    // get, clean, clear output
    val output = runner.oc.getOutput()
    runner.oc.clear()
    val cleanedOutput = runner.clean_events(output)

    // return
    InterpreterAtomBatch(null, cleanedOutput)
  end processReplierTask

  /** Internal API. Process a TestReplyBatch.
    *
    * Will process matched continuations.
    *
    * Ask and Reply events are captured by the runner output collector
    * `runner.oc`. Regular events are returned and cleared from the collector.
    *
    * @param path
    *   path of the replier task
    * @param atom
    *   reply batch to be processed
    * @return
    *   batch of regular events produced by the replier task
    */
  private def processAskerTask(path: String, atom: InterpreterRepBatch[_]): InterpreterAtomBatch[_] =

    // setup runner for task
    runner.setup(path, wf.path, null)

    // execute asker task on reply batch
    runner.run_batch(atom.list)

    // get, clean, clear output
    val output = runner.oc.getOutput()
    runner.oc.clear()
    val cleanedOutput = runner.clean_events(output)

    // return
    InterpreterAtomBatch(null, cleanedOutput)
  end processAskerTask

  /** Internal API. Process the workflow on the provided events in `_outputs`.
    *
    * To be used internally to process the workflow on previous `_outpouts` in
    * topological order.
    *
    * The provided _outputs parameter provides a mapping of task-names to their
    * output. The tasks in the workflow are then executed according to
    * topographical order to process their inputs. An example use of this
    * function is to provide the workflow input as a mapping from the source
    * name to the workflow input. This will in turn process the input on the
    * workflow, and produce a list of the produced atoms, typically this will be
    * a single InterpreterAtomBatch. If there are asker tasks, or replyer tasks,
    * then it may also produce several AskBatches and RepBatches.
    *
    * @param _outputs
    *   mapping of task names to their output
    * @return
    *   list of produced atoms by the workflow
    */
  private def processAtomBatchHelper(_outputs: Map[String, InterpreterAtomBatch[_]]): List[InterpreterAtom] =
    // A mapping from task/source/sink name to their output
    var outputs: Map[String, InterpreterAtomBatch[_]] = _outputs

    ////////////////////////////////////////////////////////////////////////////
    // 1. Execute tasks in topographical order
    ////////////////////////////////////////////////////////////////////////////
    sortedTasks.foreach { (path, task) =>
      // all inputs to the task
      val inputs = wf.connections.filter((from, to) => to == path).map(_._1)

      // process the task, add the produced events to the outputs
      val output = processTask(path, task, inputs, outputs)
      if output.list.nonEmpty then outputs += (path -> output)
    }

    ////////////////////////////////////////////////////////////////////////////
    // 2. Execute the sink
    ////////////////////////////////////////////////////////////////////////////
    {
      val inputs = wf.connections.filter((from, to) => to == wf.sink).map(_._1)
      outputs += wf.sink -> processSink(wf.sink, inputs, outputs)
    }

    ////////////////////////////////////////////////////////////////////////////
    // 3. Collect AskBatches and RepBatches from the OutputCollector
    ////////////////////////////////////////////////////////////////////////////
    val askAndReplyOutputs = {
      // collect ask outputs
      val askoutputs =
        runner.oc
          .getAskOutput()
          // group by portal
//          .groupBy { case Ask(_, portalMeta, _) => (portalMeta.portal) }
          .map(x => (x.meta.portal, List(x)))
          // map grouping to ask batches
          .map { (k, v) => InterpreterAskBatch(InterpreterPortalBatchMeta(k, wf.path), v) }
          .toList

      // collect reply outputs
      val repoutputs =
        runner.oc
          .getRepOutput()
          // group by portal and the asking workflow
          .groupBy { case Reply(_, portalMeta, _) => (portalMeta.portal, portalMeta.askingWF) }
          // map grouping to reply batches
          .map { (k, v) => InterpreterRepBatch(InterpreterPortalBatchMeta(k._1, k._2), v) }
          .toList

      // concatenate list of atom batches
      askoutputs ::: repoutputs
    }

    ////////////////////////////////////////////////////////////////////////////
    // 4. Cleanup
    ////////////////////////////////////////////////////////////////////////////
    runner.oc.clear()
    runner.oc.clearAsks()
    runner.oc.clearReps()

    ////////////////////////////////////////////////////////////////////////////
    // 5. Return
    ////////////////////////////////////////////////////////////////////////////
    outputs(wf.sink) :: askAndReplyOutputs
  end processAtomBatchHelper
