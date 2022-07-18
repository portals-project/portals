package portals

import scala.collection.mutable
import java.util.concurrent.Flow.Subscriber
import java.util.LinkedList
import scala.util.control.Breaks._
import collection.JavaConverters._
import java.{util => ju}

class SyncLocalSystem extends LocalSystemContext:
  val registry: GlobalRegistry = GlobalRegistry()

  var workflows = Map[String, RuntimeWorkflow]()

  def launch(application: Application): Unit =
    // launch workflows
    application.workflows.foreach(workflow =>
      workflows += (workflow.name -> RuntimeWorkflow.fromStaticWorkflow(this, workflow))
    )

  override def step(): Unit = workflows.filter(!_._2.isEmpty()).head._2.step()
  override def step(wf: Workflow[_, _]): Unit = workflows(wf.name).step()
  override def stepAll(): Unit = isEmpty() match {
    case true =>
    case false => {
      workflows.filter(!_._2.isEmpty()).head._2.stepAll()
      stepAll()
    }
  }
  override def stepAll(wf: Workflow[_, _]): Unit = workflows(wf.name).stepAll()
  override def isEmpty(): Boolean = workflows.forall(_._2.isEmpty())
  override def isEmpty(wf: Workflow[_, _]): Boolean = workflows(wf.name).isEmpty()

  def shutdown(): Unit = ()

/*
 * This class contains:
 *   1. Static Information:
 *      a. tasks, sources, sinks
 *      b. name to identify this RuntimeWorkflow
 *   2. Runtime Information
 *      a. buffer for sources
 *      b. connections (allowing subscription at runtime)
 *      c. ref to systemContext
 *   3. Workflow execution logic
 */
class RuntimeWorkflow(val name: String, val system: SyncLocalSystem) {
  private val logger = Logger(name)

  var tasks = Map[String, RuntimeBehavior[_, _]]()
  var sources = Map[String, RuntimeBehavior[_, _]]()
  var sourceEventBuffer = Map[String, ju.List[WrappedEvent[_]]]()
  var sourceAtomBuffer = Map[String, LinkedList[List[WrappedEvent[_]]]]()
  var sinks = Map[String, RuntimeBehavior[_, _]]()
  var connections = Map[String, Set[String]]()

  def isSource(name: String): Boolean = sources.contains(name)
  def isSink(name: String): Boolean = sinks.contains(name)
  def getRuntimeBehavior(name: String): RuntimeBehavior[_, _] = {
    tasks.getOrElse(name, sources.getOrElse(name, sinks.getOrElse(name, null)))
  }

  var stepId = 0
  var microStepId = 0

  var _nextId = 0
  def nextId(): Int =
    _nextId = _nextId + 1
    _nextId

  var executionQueue = LinkedList[(RuntimeBehavior[_, _], WrappedEvent[_])]()

  def microStep(): Unit = {
    logger.debug(s"== mstep ${microStepId}")
    while (!executionQueue.isEmpty) {
      val (cell, event) = executionQueue.poll()
      logger.debug(s"${cell.name} consume ${event}")
      cell.step(event)
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
        nonEmptySourceBuffers(selectedSource)
          .poll()
          .foreach(event => {
            executionQueue.add((sources(selectedSource), event))
            microStep()
          })
        logger.debug(s"step ${stepId} finishes")
      }
  }

  def stepAll(): Unit = {
    while (!isEmpty()) {
      step()
      stepId += 1
    }
    logger.debug("execution finished")
  }

  def isEmpty(): Boolean = sourceAtomBuffer.filter(!_._2.isEmpty).size == 0

  def stashWrappedEventToSource(name: String, event: WrappedEvent[_]): Unit = {
    // logger.debug(s"add event[${event}] to ${name}")
    sourceEventBuffer(name).add(event)
    event match {
      case Atom() =>
        val atom = sourceEventBuffer(name).asScala.toList
        sourceEventBuffer(name).clear()
        // only stash atom with at least one event
        if (atom.length > 1) {
          sourceAtomBuffer(name).add(atom)
        }
      case _ =>
    }
  }

  def onRuntimeBehaviorEventSubmit[I, O](behavior: RuntimeBehavior[I, O], event: O): Unit =
    dispatchEvent(behavior, Event(event))
  def onRuntimeBehaviorAtomSubmit(behavior: RuntimeBehavior[_, _]): Unit =
    dispatchEvent(behavior, Atom())
  def dispatchEvent(behavior: RuntimeBehavior[_, _], event: WrappedEvent[_]): Unit = {
    connections(behavior.name).foreach(toName => {
      // logger.debug(s"dispatch event[${event}] to ${toName}")
      val dstwfId = extractWfId(toName)
      // cycle to self soure or dispatch to another wf
      if (sources.contains(toName) || dstwfId != name) {
        system.workflows(dstwfId).stashWrappedEventToSource(toName, event)
      } else {
        executionQueue.add((getRuntimeBehavior(toName), event))
      }
    })
  }

  def extractWfId(name: String): String = {
    name.split("/")(0)
  }
}

// build runtime workflow from static workflow
object RuntimeWorkflow {
  def fromStaticWorkflow(system: SyncLocalSystem, wf: Workflow[_, _]): RuntimeWorkflow = {
    val rtwf = RuntimeWorkflow(wf.name, system)

    // tasks in static workflow conatins sources and sinks, remove it
    // TODO: consider doing this at AtomicStreamImpl
    val tasksInMiddle = wf.tasks.filter((k, v) => {
      !wf.sources.contains(k) && !wf.sinks.contains(k)
    })

    tasksInMiddle.foreach((name, behavior) => {
      val fullName = wf.name + "/" + name
      // println(s"task: ${fullName}")
      val rtBehavior = RuntimeBehavior(fullName, rtwf, behavior)
      rtwf.tasks += (fullName -> rtBehavior)
      system.registry.set(fullName, (rtBehavior.iref(), rtBehavior.oref()))
    })

    wf.sources.foreach((name, behavior) => {
      val fullName = wf.name + "/" + name
      // println(s"source: ${fullName}")
      val rtBehavior = RuntimeSource(fullName, rtwf, behavior)
      rtwf.sources += (fullName -> rtBehavior)
      rtwf.sourceAtomBuffer += (fullName -> new LinkedList[List[WrappedEvent[_]]]())
      rtwf.sourceEventBuffer += (fullName -> new LinkedList[WrappedEvent[_]]())
      system.registry.set(fullName, (rtBehavior.iref(), rtBehavior.oref()))
    })

    wf.sinks.foreach((name, behavior) => {
      val fullName = wf.name + "/" + name
      // println(s"sink: ${fullName}")
      val rtBehavior = RuntimeSink(fullName, rtwf, behavior)
      rtwf.sinks += (fullName -> rtBehavior)
      system.registry.set(fullName, (rtBehavior.iref(), rtBehavior.oref()))
    })

    rtwf.connections = wf.connections
      .map((k, v) => (wf.name + "/" + k, wf.name + "/" + v))
      .groupBy(_._1)
      .mapValues(_.map(_._2).toSet)
      .toMap
      .withDefaultValue(Set())

    rtwf.logger.debug(s"workflow ${rtwf.name} configuration:")
    rtwf.logger.debug(s"\tsources: ${rtwf.sources.keys.mkString(", ")}")
    rtwf.logger.debug(s"\ttasks: ${rtwf.tasks.keys.mkString(", ")}")
    rtwf.logger.debug(s"\tsinks: ${rtwf.sinks.keys.mkString(", ")}")
    rtwf.logger.debug(s"\tconnections: ")
    // TODO: print in topological order
    rtwf.connections.foreach((from, to) => {
      rtwf.logger.debug(s"\t\t${from} -> ${to.mkString(", ")}")
    })

    system.workflows += (rtwf.name -> rtwf)

    rtwf
  }
}

/*
 * This class
 *   1. wraps behavior's static information:
 *      a. unify handling of atom and event
 *      b. name, to identify this RuntimeBehavior
 *   2. maintain task's runtime information
 *      a. cell local state, callbacks (TaskContext)
 *      b. ref to runtme workflow
 */
class RuntimeBehavior[I, O](
    val name: String,
    val rtwf: RuntimeWorkflow,
    val behavior: Task[I, O]
):
  val ctx = TaskContext[I, O]()

  val self = this
  ctx.cb = new TaskCallback[I, O] {
    def submit(event: O): Unit = rtwf.onRuntimeBehaviorEventSubmit(self, event)
    def fuse(): Unit = rtwf.onRuntimeBehaviorAtomSubmit(self)
  }

  def step[T](item: WrappedEvent[T]): Unit =
    item match
      case Event(item) => {
        behavior.onNext(ctx)(item.asInstanceOf[I]) // TODO: better type cast
      }
      case Atom() => behavior.onAtomComplete(ctx)

  // NOTE: only allow source to use iref
  def iref(): IStreamRef[I] = new IStreamRef[I] {
    private[portals] def submit(event: I): Unit = ???
    private[portals] def fuse(): Unit = ???
  }

  // NOTE: only allow sink to use oref
  def oref(): OStreamRef[O] = new OStreamRef[O] {
    private[portals] def subscribe(subscriber: IStreamRef[O]): Unit = ???
  }

class RuntimeSource[I, O](
    override val name: String,
    override val rtwf: RuntimeWorkflow,
    override val behavior: Task[I, O]
) extends RuntimeBehavior[I, O](name, rtwf, behavior) {
  override def iref(): IStreamRef[I] = NamedIStreamRef(name, rtwf)
}

class NamedIStreamRef[I](val name: String, val rtwf: RuntimeWorkflow) extends IStreamRef[I] {
  private[portals] def submit(event: I): Unit = {
    rtwf.stashWrappedEventToSource(name, Event(event))
  }
  private[portals] def fuse(): Unit = {
    rtwf.stashWrappedEventToSource(name, Atom())
  }
}

class RuntimeSink[I, O](
    override val name: String,
    override val rtwf: RuntimeWorkflow,
    override val behavior: Task[I, O]
) extends RuntimeBehavior[I, O](name, rtwf, behavior) {
  var preSubmitCallback = new PreSubmitCallback[O] {}

  ctx.cb = new TaskCallback[I, O] {
    def submit(event: O): Unit = {
      preSubmitCallback.preSubmit(event)
      rtwf.onRuntimeBehaviorEventSubmit(self, event)
    }
    def fuse(): Unit = {
      preSubmitCallback.preFuse()
      rtwf.onRuntimeBehaviorAtomSubmit(self)
    }
  }

  override def oref(): OStreamRef[O] = new OStreamRef[O] {
    private[portals] override def setPreSubmitCallback(cb: PreSubmitCallback[O]): Unit = preSubmitCallback = cb

    private[portals] def subscribe(subscriber: IStreamRef[O]): Unit =
      val runtimeSubscriber = subscriber.asInstanceOf[NamedIStreamRef[_]]
      // println(s"cross wf subscription from ${name} to ${runtimeSubscriber.name}")
      rtwf.connections = rtwf.connections.updatedWith(name) {
        case Some(toSet) => {
          Some(toSet + runtimeSubscriber.name)
        }
        case None => {
          Some(Set(runtimeSubscriber.name))
        }
      }
  }
}
