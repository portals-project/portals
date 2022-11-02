package portals.system.test

import portals.*

private class CollectingTaskCallBack extends TaskCallback[Any, Any]:
  private var _output = List.empty[WrappedEvent[Any]]

  def submit(key: Key[Int], event: Any): Unit = _output = Event(key, event) :: _output

  def fuse(): Unit = _output = Atom :: _output

  def getOutput(): List[WrappedEvent[Any]] = _output.reverse

  def clear(): Unit = _output = List.empty

class TestWorkflow(wf: Workflow[_, _])(using rctx: TestRuntimeContext):
  /** Processes the atom, and produces a new list of atoms.
    *
    * The produced list of atoms may either be a regular atom for an output atomic stream, or an atom for a portal. See
    * the `TestAtom` trait for the distinction.
    */
  def process(atom: TestAtom): List[TestAtom] = atom match
    case a @ TestAtomBatch(path, list) => processAtom(a)

  /** Internal API. Processes a TestAtomBatch. */
  private def processAtom(atom: TestAtomBatch[_]): List[TestAtom] =
    // Info: The atom is processed in a depth-first traversal down the DAG starting
    // from the root (i.e., source).
    // For each node (i.e., task) we process the whole batch, this, in turn,
    // produces a batch of new events, these are then further processed by the
    // chained tasks in depth-first order.

    // TODO: we should probably not do this recursively, it would be easier to follow the code if it was
    // done with a while loop.
    def recProcess(path: String, atom: TestAtomBatch[_]): List[TestAtom] =
      // if the path is a sink, then simply return the batch of events with correct path
      if wf.sinks.contains(path) then List(TestAtomBatch(wf.stream.path, atom.list))
      else // else execute the task, and execute downstream tasks recursively on the output

        val task = wf.tasks(path)
        val tcb = CollectingTaskCallBack()
        val tctx = TaskContext[Any, Any]()
        tctx.cb = tcb

        // TODO: this should only be executed once
        val preppedTask = Tasks.prepareTask(task, tctx.asInstanceOf)

        atom.list.foreach { event =>
          event match
            case Event(key, e) =>
              tctx.key = key
              tctx.state.key = key
              preppedTask.onNext(using tctx.asInstanceOf)(e.asInstanceOf)
            case Atom => preppedTask.onAtomComplete(using tctx.asInstanceOf)
            case Seal => preppedTask.onComplete(using tctx.asInstanceOf)
            case Error(t) => preppedTask.onError(using tctx.asInstanceOf)(t)
        }

        // execute on all children
        val connectionsFromThisPath = wf.connections.filter((from, to) => from == path).map((from, to) => to)
        connectionsFromThisPath.toList.flatMap { to =>
          recProcess(to, TestAtomBatch(atom.path, tcb.getOutput()))
        }

    // TODO: currently, we may have multiple sources, this should change...
    // For each source, we invoke the recursive method recProcess to process the atom.
    wf.sources.toList.flatMap { (path, task) =>
      val connectionsFromThisPath = wf.connections.filter((from, to) => from == path)
      connectionsFromThisPath.flatMap { (_, to) =>
        recProcess(to, TestAtomBatch(atom.path, atom.list))
      }
    }
