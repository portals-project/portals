package portals.system.test

import portals.*

private class CollectingTaskCallBack[T, U] extends TaskCallback[T, U]:
  private var _output = List.empty[WrappedEvent[U]]

  def submit(key: Key[Int], event: U): Unit = _output = Event(key, event) :: _output

  // deprecated :), remove it
  // def fuse(): Unit = ()

  def putEvent(event: WrappedEvent[U]): Unit = _output = event :: _output

  def getOutput(): List[WrappedEvent[U]] = _output.reverse

  def clear(): Unit = _output = List.empty

private class CollectingPortalTaskCallBack[T, U, Req, Rep]
    extends CollectingTaskCallBack[T, U]
    with PortalTaskCallback[T, U, Req, Rep]:
  private var _asks = List.empty[Ask[Req]]
  private var _reps = List.empty[Reply[Rep]]

  override def ask(portal: AtomicPortalRefKind[Req, Rep])(req: Req)(key: Key[Int], id: Int): Unit =
    _asks = Ask(key, portal.path, id, req) :: _asks

  override def reply(r: Rep)(key: Key[Int], id: Int): Unit =
    _reps = Reply(key, null, id, r) :: _reps

  def getAskOutput(): List[Ask[Req]] = _asks.reverse
  def getRepOutput(): List[Reply[Rep]] = _reps.reverse
  def clearAsks(): Unit =
    _asks = List.empty
  def clearReps(): Unit =
    _reps = List.empty

class TestWorkflow(wf: Workflow[_, _])(using rctx: TestRuntimeContext):
  private val tcb = CollectingTaskCallBack[Any, Any]()
  private val tctx = TaskContext[Any, Any]()
  tctx.cb = tcb
  private val pcb = CollectingPortalTaskCallBack[Any, Any, Any, Any]() // for portals
  private var continuations = Map.empty[Int, Continuation[Any, Any, Any, Any]]
  private var futures = Map.empty[Int, FutureImpl[Any]]

  /** Gets the ordinal of a path with respect to the topology of the graph.
    *
    * To be used to sort the graph in topological order.
    */
  private def getOrdinal(path: String): Int =
    val idx = wf.connections.reverse.map(_._1).indexOf(path)
    if idx == -1 then wf.connections.reverse.size else idx

  // topographically sorted according to connections
  private val sortedTasks = wf.tasks.toList.sortWith((t1, t2) => getOrdinal(t1._1) < getOrdinal(t2._1))
  // and initialized / prepared
  private val initializedTasks = sortedTasks.map { (name, task) =>
    (name, Tasks.prepareTask(task, tctx.asInstanceOf))
  }
  tcb.clear()

  /** Processes the atom, and produces a new list of atoms.
    *
    * The produced list of atoms may either be a regular atom for an output atomic stream, or an atom for a portal. See
    * the `TestAtom` trait for the distinction.
    */
  def process(atom: TestAtom): List[TestAtom] =
    atom match
      case a @ TestAtomBatch(path, list) => processAtom(a)
      case ask @ TestPortalAskBatch(portal, sendr, recvr, list) => processAsk(ask)
      case reply @ TestPortalRepBatch(portal, sendr, recvr, list) => processReply(reply)

  /** Internal API. Process a TestAskBatch. */
  private def processAsk(atom: TestPortalAskBatch[_]): List[TestAtom] =
    // val taskName = wf.tasks
    //   .filter((name, task) =>
    //     task match
    //       case AskerTask(_) => true
    //       case _ => false
    //   )
    //   .head
    //   ._1

    // atom.list.foreach { event =>
    //   event match
    //     case Reply(key, path, id, e) =>
    //       tctx.key = key
    //       tctx.state.key = key
    //       val actx = AskerTaskContext.fromTaskContext(tctx)(pcb)
    //       val tazk = continuations(id)(using actx)
    //       tazk.onNext(using actx.asInstanceOf[TaskContext[tazk._T, tazk._U]])(e.asInstanceOf)
    //     case _ => ??? // NOPE
    // }

    // val askOutput = pcb.getAskOutput()
    // val output = pcb.getOutput()
    // pcb.clear()

    // processAtomHelper(Map(taskName -> TestAtomBatch(atom.portal, output)))

    val (name, task) = initializedTasks
      .filter((name, task) =>
        task match
          case ReplierTask(f1, f2) => true
          case AskerTask(f) => false
          case _ => false
      )
      .head

    atom.list.foreach { event =>
      event match
        case Ask(key, path, id, e) =>
          tctx.key = key
          tctx.state.key = key
          val rctx = ReplierTaskContext.fromTaskContext(tctx)(pcb, atom.portal)
          rctx.id = id
          task.asInstanceOf[ReplierTask[_, _, _, _]].f2(rctx.asInstanceOf)(e.asInstanceOf)
        // task.onNext(using rctx.asInstanceOf[TaskContext[task._T, task._U]])(e.asInstanceOf)
        case _ => ???
    }

    val output = pcb.getOutput()
    pcb.clear()

    val outputs = processAtomHelper(Map(name -> TestAtomBatch(atom.portal, output)))
    val x = outputs.map(_atom =>
      _atom match
        case TestAtomBatch(path, list) => _atom
        case TestPortalAskBatch(portal, sendr, recvr, list) => _atom
        case TestPortalRepBatch(portal, sendr, recvr, list) =>
          TestPortalRepBatch(atom.portal, atom.recvr, atom.sendr, list)
    )
    x
    // outputs
    // if output.isEmpty then List.empty
    // else
    //   List(
    //     TestPortalRepBatch(
    //       atom.portal,
    //       atom.recvr,
    //       atom.sendr,
    //       output.map(r => Reply(r.key, atom.portal, r.id, r.event)),
    //     )
    //   )

  /** Internal API. Process a TestReplyBatch. */
  private def processReply(atom: TestPortalRepBatch[_]): List[TestAtom] =
    val taskName = wf.tasks
      .filter((name, task) =>
        task match
          case AskerTask(_) => true
          case _ => false
      )
      .head
      ._1

    atom.list.foreach { event =>
      event match
        case Reply(key, path, id, e) =>
          tctx.key = key
          tctx.state.key = key
          val actx = AskerTaskContext.fromTaskContext(tctx)(pcb)
          futures(id)._value = Some(e)
          continuations(id)(using actx)
          continuations -= id
          futures -= id
          continuations ++= actx._continuations
          futures ++= actx._futures.asInstanceOf[Map[Int, FutureImpl[Any]]]
        case _ => ??? // NOPE
    }

    val output = pcb.getOutput()
    pcb.clear()

    processAtomHelper(Map(taskName -> TestAtomBatch(atom.portal, output)))

  // TODO: deduplicate
  /** Internal API. Processes a TestAtomBatch. */
  private def processAtomHelper(_outputs: Map[String, TestAtomBatch[_]]): List[TestAtom] =
    var outputs: Map[String, TestAtomBatch[_]] = _outputs

    // tasks
    sortedTasks.foreach { (path, task) =>
      val froms = wf.connections.filter((from, to) => to == path).map(_._1)
      var seald = false
      var errord = false
      var atomd = false

      froms.foreach { from =>
        val atom = outputs.getOrElse(from, TestAtomBatch(null, List.empty))
        atom.list.foreach { event =>
          event match
            case Event(key, e) =>
              task match {
                case _: AskerTask[_, _, _, _] =>
                  tctx.key = key
                  tctx.state.key = key
                  val actx = AskerTaskContext.fromTaskContext(tctx)(pcb)
                  task.onNext(using actx.asInstanceOf[TaskContext[task._T, task._U]])(e.asInstanceOf)
                  continuations ++= actx._continuations
                  futures ++= actx._futures.asInstanceOf[Map[Int, FutureImpl[Any]]]
                case _: Task[?, ?] =>
                  tctx.key = key
                  tctx.state.key = key
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
        tcb.putEvent(Seal)
      else if atomd then
        task.onAtomComplete(using tctx.asInstanceOf)
        tcb.putEvent(Atom)

      val output = tcb.getOutput()
      tcb.clear()
      outputs += path -> TestAtomBatch(wf.stream.path, output)
    }

    // sink
    {
      val toSinks = wf.connections.filter((from, to) => to == wf.sink).map(_._1)
      var _output = List.empty[WrappedEvent[_]]
      var atomd = false
      var seald = false
      var errord = false

      toSinks.foreach { from =>
        val atom = outputs(from)
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

      outputs += wf.sink -> TestAtomBatch(wf.stream.path, _output.reverse)
    }

    // portal inputs/outputs
    val portalOutputs = {
      val askoutput =
        pcb.getAskOutput().groupBy(e => e.path).map { (k, v) => TestPortalAskBatch(k, wf.path, k, v) }.toList
      val repoutput =
        pcb.getRepOutput().groupBy(e => e.path).map { (k, v) => TestPortalRepBatch(k, wf.path, k, v) }.toList
      pcb.clearAsks()
      pcb.clearReps()
      askoutput ::: repoutput
    }

    outputs(wf.sink) :: portalOutputs
  end processAtomHelper

  /** Internal API. Processes a TestAtomBatch. */
  private def processAtom(atom: TestAtomBatch[_]): List[TestAtom] =
    var outputs: Map[String, TestAtomBatch[_]] = Map.empty

    // source
    {
      outputs += wf.source -> atom
    }

    // tasks
    sortedTasks.foreach { (path, task) =>
      val froms = wf.connections.filter((from, to) => to == path).map(_._1)
      var seald = false
      var errord = false
      var atomd = false

      froms.foreach { from =>
        val atom = outputs(from)
        atom.list.foreach { event =>
          event match
            case Event(key, e) =>
              task match {
                case _: AskerTask[_, _, _, _] =>
                  tctx.key = key
                  tctx.state.key = key
                  val actx = AskerTaskContext.fromTaskContext(tctx)(pcb)
                  task.onNext(using actx.asInstanceOf[TaskContext[task._T, task._U]])(e.asInstanceOf)
                  continuations ++= actx._continuations
                  futures ++= actx._futures.asInstanceOf[Map[Int, FutureImpl[Any]]]
                case _: Task[?, ?] =>
                  tctx.key = key
                  tctx.state.key = key
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
        tcb.putEvent(Seal)
      else if atomd then
        task.onAtomComplete(using tctx.asInstanceOf)
        tcb.putEvent(Atom)

      val output = tcb.getOutput()
      tcb.clear()
      outputs += path -> TestAtomBatch(wf.stream.path, output)
    }

    // sink
    {
      val toSinks = wf.connections.filter((from, to) => to == wf.sink).map(_._1)
      var _output = List.empty[WrappedEvent[_]]
      var atomd = false
      var seald = false
      var errord = false

      toSinks.foreach { from =>
        val atom = outputs(from)
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

      outputs += wf.sink -> TestAtomBatch(wf.stream.path, _output.reverse)
    }

    // portal inputs/outputs
    val portalOutputs = {
      val askoutput =
        pcb.getAskOutput().groupBy(e => e.path).map { (k, v) => TestPortalAskBatch(k, wf.path, k, v) }.toList
      val repoutput =
        pcb.getRepOutput().groupBy(e => e.path).map { (k, v) => TestPortalRepBatch(k, wf.path, k, v) }.toList
      pcb.clearAsks()
      pcb.clearReps()
      askoutput ::: repoutput
    }

    outputs(wf.sink) :: portalOutputs
  end processAtom
