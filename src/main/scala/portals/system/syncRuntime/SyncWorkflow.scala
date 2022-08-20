package portals

import java.{util => ju}
import java.util.LinkedList

import collection.JavaConverters._

trait SyncWorkflow extends Executable with Recvable {
  val staticWf: Workflow[_, _]
}

class RuntimeWorkflow(val staticWf: Workflow[_, _]) extends SyncWorkflow {
  private val logger = Logger(staticWf.path)

  // if the workflow has been stopped due to a seal
  private var stopped = false

  var tasks = Map[String, RuntimeBehavior[_, _]]()
  var sources = Map[String, RuntimeBehavior[_, _]]()
  var sinkEventBuffer = Map[String, ju.List[WrappedEvent[_]]]()
  var sourceAtomBuffer = Map[String, LinkedList[EventBatch]]()
  var sinks = Map[String, RuntimeBehavior[_, _]]()
  var connections = Map[String, Set[String]]()
  var subscribers = List[Recvable]()

  // TODO: assume only one source
  def recv(from: AtomicStreamRefKind[_], seq: EventBatch) = {
    logger.debug(s"${staticWf.path} receives from ${from.path}")
    sourceAtomBuffer.head._2.add(seq)
  }

  def subscribedBy(recvable: Recvable) = {
    subscribers ::= recvable
  }

  def getRuntimeBehavior(name: String): RuntimeBehavior[_, _] = {
    tasks.getOrElse(name, sources.getOrElse(name, sinks.getOrElse(name, null)))
  }

  var stepId = 0
  var microStepId = 0

  var _nextId = 0
  def nextId(): Int =
    _nextId = _nextId + 1
    _nextId

  // eventFrom, currentCell, event
  var executionQueue = LinkedList[(RuntimeBehavior[_, _], WrappedEvent[_])]()

  def microStep(): Unit = {
    logger.debug(s"== mstep ${microStepId}")
    while (!executionQueue.isEmpty) {
      val (currentCell, event) = executionQueue.poll()
      logger.debug(s"${currentCell.name} consume ${event}")
      currentCell.step(event)
    }
    microStepId += 1
  }

  def step(): Unit = {
    isEmpty() match
      case true =>
        logger.error("no event to execute")
      case false => {
        // select one source with non-empty buffer, poll its events until atom is met
        logger.debug(s"==== step ${stepId}")
        val nonEmptySourceBuffers = sourceAtomBuffer.filter(!_._2.isEmpty)
        val selectedSource = nonEmptySourceBuffers.map(_._1).head
        logger.debug(s"consume one atom(event seq) from ${selectedSource}")
        val atomSeq = sourceAtomBuffer(selectedSource).poll()
        atomSeq match
          case AtomSeq(seq) =>
            seq.foreach(event => {
              executionQueue.add((sources(selectedSource), event))
              microStep()
            })
          case SealSeq =>
            executionQueue.add((sources(selectedSource), Seal))
            microStep()
            stopped = true // stop the workflow
        logger.debug(s"step ${stepId} finished")
      }
    stepId += 1
  }

  def stepAll(): Unit = {
    while (!isEmpty()) {
      step()
    }
    logger.debug("execution finished")
  }

  // TODO if picked an empty Atom
  def isEmpty(): Boolean = {
    // logger.debug(s"isEmpty returns ${sourceAtomBuffer.filter(_._2.isEmpty).size == sourceAtomBuffer.size}")
    // workflow does not process events if it is stopped
    sourceAtomBuffer.filter(_._2.isEmpty).size > 0 | stopped
  }

  def onRuntimeBehaviorEventSubmit[I, O](behavior: RuntimeBehavior[I, O], key: Key[Int], event: O): Unit =
    dispatchEvent(behavior, Event(key, event))
  def onRuntimeBehaviorAtomSubmit(behavior: RuntimeBehavior[_, _]): Unit =
    dispatchEvent(behavior, Atom)
  def dispatchEvent(behavior: RuntimeBehavior[_, _], event: WrappedEvent[_]): Unit = {
    // if is sink
    if (sinks.contains(behavior.name)) {
      sinkEventBuffer(behavior.name).add(event)
      event match {
        case Atom =>
          val atom = sinkEventBuffer(behavior.name).asScala.toList
          sinkEventBuffer(behavior.name).clear()
          // only broadcast to subscribers if the atomSeq is not empty
          if (atom.length > 1) {
            subscribers.foreach(_.recv(staticWf.stream, AtomSeq(atom)))
          }
        case Seal =>
          val atom = sinkEventBuffer(behavior.name).asScala.toList
          sinkEventBuffer(behavior.name).clear()
          subscribers.foreach(_.recv(staticWf.stream, SealSeq))
        case _ =>
      }
    } else {
      connections(behavior.name).foreach(toName => {
        executionQueue.add((getRuntimeBehavior(toName), event))
      })
    }
  }
}

// build runtime workflow from static workflow
object RuntimeWorkflow {
  def fromStaticWorkflow(app: Application, wf: Workflow[_, _]): RuntimeWorkflow = {
    val rtwf = RuntimeWorkflow(wf)

    // tasks in static workflow conatins sources and sinks, remove it
    // TODO: consider doing this at AtomicStreamImpl
    val tasksInMiddle = wf.tasks.filter((k, v) => {
      !wf.sources.contains(k) && !wf.sinks.contains(k)
    })

    tasksInMiddle.foreach((name, behavior) => {
      val fullName = wf.path + "/" + name
      val upStreamCnt = wf.connections.filter(_._2 == name).size
      val rtBehavior = RuntimeBehavior(fullName, rtwf, behavior, upStreamCnt)
      rtwf.tasks += (fullName -> rtBehavior)
    })

    wf.sources.foreach((name, behavior) => {
      val fullName = wf.path + "/" + name
      val rtBehavior = RuntimeBehavior(fullName, rtwf, behavior, 0)
      rtwf.sources += (fullName -> rtBehavior)
      rtwf.sourceAtomBuffer += (fullName -> new LinkedList[EventBatch]())
    })

    wf.sinks.foreach((name, behavior) => {
      val fullName = wf.path + "/" + name
      val upStreamCnt = wf.connections.filter(_._2 == name).size
      val rtBehavior = RuntimeBehavior(fullName, rtwf, behavior, upStreamCnt)
      rtwf.sinks += (fullName -> rtBehavior)
      rtwf.sinkEventBuffer += (fullName -> new LinkedList[WrappedEvent[_]]())
    })

    rtwf.connections = wf.connections
      .map((k, v) => (wf.path + "/" + k, wf.path + "/" + v))
      .groupBy(_._1)
      .mapValues(_.map(_._2).toSet)
      .toMap
      .withDefaultValue(Set())

    rtwf.logger.debug(s"workflow ${wf.path} configuration:")
    rtwf.logger.debug(s"\tsources: ${rtwf.sources.keys.mkString(", ")}")
    rtwf.logger.debug(s"\ttasks: ${rtwf.tasks.keys.mkString(", ")}")
    rtwf.logger.debug(s"\tsinks: ${rtwf.sinks.keys.mkString(", ")}")
    rtwf.logger.debug(s"\tconnections: ")
    // TODO: print in topological order
    rtwf.connections.foreach((from, to) => {
      rtwf.logger.debug(s"\t\t${from} -> ${to.mkString(", ")}")
    })

    rtwf
  }
}

class RuntimeBehavior[I, O](
    val name: String,
    val rtwf: RuntimeWorkflow,
    val behavior: Task[I, O],
    val upStreamCnt: Int
):
  val ctx = TaskContext[I, O]()
  var currentAtomCnt = 0

  val self = this
  ctx.cb = new TaskCallback[I, O] {
    def submit(key: Key[Int], event: O): Unit = rtwf.onRuntimeBehaviorEventSubmit(self, key, event)
    def fuse(): Unit = rtwf.onRuntimeBehaviorAtomSubmit(self)
  }

  def step[T](item: WrappedEvent[T]): Unit =
    item match
      case Event(key, item) => {
        ctx.key = key
        ctx.state.key = key
        behavior.onNext(using ctx)(item.asInstanceOf[I]) // TODO: better type cast
      }
      case Atom => {
        // not possible to stash more than one atom in each buffer at sync runtime, no need to stash
        if (upStreamCnt <= 1) {
          behavior.onAtomComplete(using ctx)
        } else {
          currentAtomCnt += 1
          if (currentAtomCnt == upStreamCnt) {
            behavior.onAtomComplete(using ctx)
            currentAtomCnt = 0
          }
        }
      }
      case Seal =>
        behavior.onComplete(using ctx)
        rtwf.dispatchEvent(self, Seal)
      case Error(t) =>
        behavior.onError(using ctx)(t)
        rtwf.dispatchEvent(self, Error(t))
