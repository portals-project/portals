package portals.system.test

import portals.*
import portals.Generator

// trait TestProcessor:
//   def process(): List[TestAtom] = ???

//   /** Processes the atom, and produces a new list of atoms. The produced list of atoms may either be a regular atom for
//     * an output atomic stream, or an atom for a portal. See the `TestAtom` trait for the distinction.
//     */
//   def process(atom: TestAtom): List[TestAtom] = ???

//   var _inputs: Set[String]

//   /** Returns a set of inputs of a processor to the index. */
//   def inputs: Set[String]

//   /** Returns true if the processor has some input from one of the dependencies. */
//   def hasInput(): Boolean

//   /** Performs GC and cleans up any left-over data that is no longer needed. */
//   def cleanup(): Unit

case class TestWorkflow(wf: Workflow[_, _])(using rctx: TestRuntimeContext):
  def process(atom: TestAtom): List[TestAtom] = atom match
    case a @ TestAtomBatch(path, list) => processAtom(a)
    // case p @ TestPortalBatch(path, list) => processPortal(p)

  private def processAtom(atom: TestAtomBatch[_]): List[TestAtom] =
    // The atom is processed in a depth-first traversal down the DAG starting
    // from the root (i.e., source).
    // For each node (i.e., task) we process the whole batch, this, in turn,
    // produces a batch of new events, these are then further processed by the
    // chained tasks in depth-first order.

    def recProcess(atom: TestAtomBatch[_]): List[TestAtom] =
      // if the path is a sink, then simply return the batch of events with correct path
      if wf.sinks.contains(atom.path) then List(TestAtomBatch(wf.stream.path, atom.list))
      else
        // execute task
        val task = wf.tasks(atom.path)

        var output = List.empty[WrappedEvent[Any]]
        // construct side effect collecting task cb
        val tcb = new TaskCallback[Any, Any] {
          def submit(key: Key[Int], event: Any): Unit =
            output = Event(key, event) :: output

          def fuse(): Unit =
            output = Atom :: output
        }

        // construct task context
        val tctx = TaskContext[Any, Any]()
        tctx.cb = tcb

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
            // case _ => ??? // not handled for now hehe
        }

        output = (Atom :: output).reverse

        // execute on all children
        val connectionsFromThisPath = wf.connections.filter((from, to) => from == atom.path).map((from, to) => to)
        connectionsFromThisPath.toList.flatMap { to =>
          recProcess(TestAtomBatch(to, output))
        }

    // TODO: currently, we may have multiple sources, this should change...
    // For each source, we invoke the recursive method recProcess to process the atom.
    wf.sources.toList.flatMap { (path, task) =>
      val connectionsFromThisPath = wf.connections.filter((from, to) => from == path)
      connectionsFromThisPath.flatMap { (_, to) =>
        recProcess(TestAtomBatch(to, atom.list))
      }
    }

  // private def processPortal(atom: TestPortalBatch[_]) = ???

  // var _inputs: Map[String, Long] = Map(wf.consumes.path -> -1)
  // def inputs: Map[String, Long] = _inputs

  // def hasInput(): Boolean =
  //   rctx.streams
  //     .filter((path, stream) => this.inputs.contains(path))
  //     .exists((path, stream) => inputs(path) < stream.getIdxRange()._2)

  // def cleanup(): Unit = () // nothing to clean up :)

// @main def testThisFile(): Unit =
//   val wf = ApplicationBuilders
//     .application("asdf")
//     .workflows[Int, Int]()
//     .source(null)
//     .map[Int] { ctx ?=> event =>
//       ctx.log.info("asdf" + event); event
//     }
//     .map { event => event }
//     .logger()
//     .sink()
//     .freeze()

//   val testWF = TestWorkflow(wf)

//   val testEvents = TestAtomBatch("doesn't matter :)", List(Event(Key(0), 0), Event(Key(1), 1), Seal, Atom))

//   testWF.process(testEvents)
