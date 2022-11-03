package portals.system.test

import portals.*

private class CollectingTaskCallBack[T, U] extends TaskCallback[T, U]:
  private var _output = List.empty[WrappedEvent[U]]

  def submit(key: Key[Int], event: U): Unit = _output = Event(key, event) :: _output

  // deprecated :), remove it
  def fuse(): Unit = ()

  def putEvent(event: WrappedEvent[U]): Unit = _output = event :: _output

  def getOutput(): List[WrappedEvent[U]] = _output.reverse

  def clear(): Unit = _output = List.empty

class TestWorkflow(wf: Workflow[_, _])(using rctx: TestRuntimeContext):
  private val tcb = CollectingTaskCallBack[Any, Any]()
  private val tctx = TaskContext[Any, Any]()
  tctx.cb = tcb

  // get the ordinal of a path using the workflow's connections, which are sorted
  private def getOrdinal(path: String): Int =
    val idx = wf.connections.reverse.map(_._1).indexOf(path)
    if idx == -1 then wf.connections.reverse.size else idx

  // topographically sorted according to connections
  private val sortedTasks = wf.tasks.toList.sortWith((t1, t2) => getOrdinal(t1._1) < getOrdinal(t2._1))

  /** Processes the atom, and produces a new list of atoms.
    *
    * The produced list of atoms may either be a regular atom for an output atomic stream, or an atom for a portal. See
    * the `TestAtom` trait for the distinction.
    */
  def process(atom: TestAtom): List[TestAtom] =
    atom match
      case a @ TestAtomBatch(path, list) => processAtom(a)

  /** Internal API. Processes a TestAtomBatch. */
  private def processAtom(atom: TestAtomBatch[_]): List[TestAtom] =
    // atoms that are produced and consumed
    var outputs: Map[String, TestAtomBatch[_]] = Map.empty

    // for every source, this will change :), add the atom to its output
    wf.sources.foreach { src =>
      outputs += src._1 -> atom
    }

    // for each task, process all inputs, and add output to outputs
    sortedTasks.concat(wf.sinks).foreach { (path, task) =>
      val froms = wf.connections.filter((from, to) => to == path).map(_._1)
      var seald = false
      var errord = false
      var atomd = false
      // process each input from a from
      froms.foreach { from =>
        val atom = outputs(from)
        atom.list.foreach { event =>
          event match
            case Event(key, e) =>
              tctx.key = key
              tctx.state.key = key
              task.onNext(using tctx.asInstanceOf)(e.asInstanceOf)
            case Atom =>
              atomd = true
            case Seal =>
              seald = true
            case Error(t) =>
              errord = true
              task.onError(using tctx.asInstanceOf)(t)
        }
      }
      if errord then ???
      else if seald then
        task.onComplete(using tctx.asInstanceOf)
        tcb.putEvent(Seal)
      else if atomd then
        task.onAtomComplete(using tctx.asInstanceOf)
        tcb.putEvent(Atom)

      val output = tcb.getOutput()
      tcb.clear()
      outputs += path -> TestAtomBatch(wf.stream.path, output)
    }

    // TODO: also single sink, will change things :).
    wf.sinks.map(_._1).map(outputs(_)).toList

  //   // Info: The atom is processed in a depth-first traversal down the DAG starting
  //   // from the root (i.e., source).
  //   // For each node (i.e., task) we process the whole batch, this, in turn,
  //   // produces a batch of new events, these are then further processed by the
  //   // chained tasks in depth-first order.

  //   // TODO: we should probably not do this recursively, it would be easier to follow the code if it was
  //   // done with a while loop.
  //   def recProcess(path: String, atom: TestAtomBatch[_]): List[TestAtom] =
  //     // if the path is a sink, then simply return the batch of events with correct path
  //     if wf.sinks.contains(path) then List(TestAtomBatch(wf.stream.path, atom.list))
  //     else // else execute the task, and execute downstream tasks recursively on the output

  //       val task = wf.tasks(path)
  //       val tcb = CollectingTaskCallBack()
  //       val tctx = TaskContext[Any, Any]()
  //       tctx.cb = tcb

  //       // TODO: this should only be executed once
  //       val preppedTask = Tasks.prepareTask(task, tctx.asInstanceOf)

  //       atom.list.foreach { event =>
  //         event match
  //           case Event(key, e) =>
  //             tctx.key = key
  //             tctx.state.key = key
  //             preppedTask.onNext(using tctx.asInstanceOf)(e.asInstanceOf)
  //           case Atom => preppedTask.onAtomComplete(using tctx.asInstanceOf)
  //           case Seal => preppedTask.onComplete(using tctx.asInstanceOf)
  //           case Error(t) => preppedTask.onError(using tctx.asInstanceOf)(t)
  //       }

  //       // execute on all children
  //       val connectionsFromThisPath = wf.connections.filter((from, to) => from == path).map((from, to) => to)
  //       connectionsFromThisPath.toList.flatMap { to =>
  //         recProcess(to, TestAtomBatch(atom.path, tcb.getOutput()))
  //       }

  //   // TODO: currently, we may have multiple sources, this should change...
  //   // For each source, we invoke the recursive method recProcess to process the atom.
  //   wf.sources.toList.flatMap { (path, task) =>
  //     val connectionsFromThisPath = wf.connections.filter((from, to) => from == path)
  //     connectionsFromThisPath.flatMap { (_, to) =>
  //       recProcess(to, TestAtomBatch(atom.path, atom.list))
  //     }
  //   }
