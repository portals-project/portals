package portals.runtime.parallel

import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior

import portals.application.Application
import portals.runtime.interpreter.*
import portals.runtime.parallel.ParallelRuntime.Cache
import portals.runtime.BatchedEvents.*
import portals.runtime.SuspendingRuntime
import portals.runtime.SuspendingRuntime.*
import portals.runtime.WrappedEvents.*
import portals.util.Common.Types.Path

object ParallelRuntimePartition:
  /** Event types for the parallel runtime partition manager. */
  object Events:
    /** Internal API. Message type for the parallel runtime partition manager.
      */
    sealed trait Message extends Serializable

    /** Akka Actor commands for this behavior. */
    sealed trait Command extends Message

    /** Launch the application. */
    case class Launch(application: Application, replyTo: ActorRef[Unit]) extends Command

    /** Process the next step. */
    case object Step extends Command

    /** Stop processing, temporarily. */
    case class Stop(replyTo: ActorRef[Unit]) extends Command

    /** Start processing. */
    case class Start(replyTo: ActorRef[Unit]) extends Command

    /** Shuffled `batch` of events for `partition`. */
    case class Shuffle(partition: Int, batch: EventBatch) extends Command

  object Types:
    type Partition = Int

  // TODO MOVE ELSEWHERE
  def safeModulo(n: Int, m: Int): Int =
    val res = n % m
    if res < 0 then res + m else res

  def safeModulo(n: Long, m: Long): Long =
    val res = n % m
    if res < 0 then res + m else res

class ParallelRuntimePartition(
    partition: Int,
    nPartitions: Int,
    ctx: ActorContext[ParallelRuntimePartition.Events.Message]
) extends AbstractBehavior[ParallelRuntimePartition.Events.Message](ctx):
  import ParallelRuntimePartition.*
  import ParallelRuntimePartition.Events.*

  private val _ictx: InterpreterContext = new InterpreterContext()
  private val interpreter: Interpreter = new Interpreter(_ictx)

  private val cache = new Cache[Int, ActorRef[Message]]()

  // STATE
  private var _shuffleMap: Map[Path, List[ShuffleBatch[_]]] = Map.empty.withDefaultValue(Nil)
  private var _shuffleState: Map[Path, Int] = Map.empty.withDefaultValue(0)
  private var _intermediateMap: Map[Path, List[EventBatch]] = Map.empty.withDefaultValue(Nil)
  private var stop = true

  // TODO: make part of config
  private inline val GC_INTERVAL = 4

  /** The current step number of the execution. */
  private var _stepN: Long = 0
  private def stepN(): Long =
    _stepN += 1
    _stepN

  override def onMessage(msg: Message): Behavior[Message] =
    msg match
      case Launch(app, replyTo) =>
        this.launch(app)
        replyTo ! ()
        Behaviors.same
      case Step =>
        if stop then //
          Behaviors.same
        else
          this.step()
          ctx.self ! Step
          if stepN() % GC_INTERVAL == 0 then interpreter.garbageCollect()
          Behaviors.same
      case Stop(replyTo) =>
        stop = true
        replyTo ! ()
        Behaviors.same
      case Start(replyTo) =>
        stop = false
        replyTo ! ()
        ctx.self ! Step
        Behaviors.same
      case Shuffle(_, ShuffleBatch(path, task, list)) =>
        this.resume(path, task, list)
        Behaviors.same
      case Shuffle(_, batch) =>
        this.feed(batch)
        Behaviors.same

  def launch(app: Application): Unit =
    interpreter.launch(app)

  def step(): Unit =
    if interpreter.canStep() then
      interpreter.step() match
        case Completed(path, outputs) =>
          this.distribute(outputs)
        case Suspended(path, shuffles, intermediate) =>
          this.shuffle(path, shuffles, intermediate)

  def resume(path: Path, task: Path, list: List[WrappedEvent[_]]): Unit =
    _shuffleState = _shuffleState.updated(path, _shuffleState(path) + 1) // FIXME: should consider task also
    val batch = ShuffleBatch(path, task, list)
    _shuffleMap = _shuffleMap.updated(path, batch :: _shuffleMap(path))
    if _shuffleState(path) == nPartitions then
      val batches = _shuffleMap(path)
      _shuffleMap = _shuffleMap - path
      _shuffleState = _shuffleState - path
      val batch = ShuffleBatch(path, task, batches.flatMap(_.list))
      interpreter.resume(path, List(batch)) match
        case Completed(path, outputs) =>
          val merged = outputs ::: _intermediateMap(path)
          this.distribute(merged)
        case Suspended(path, shuffles, intermediate) =>
          this.shuffle(path, shuffles, intermediate)

  def feed(batch: EventBatch): Unit =
    interpreter.feedAtoms(List(batch))

  private def shuffle(path: Path, shuffles: List[ShuffleBatch[_]], intermediate: List[EventBatch]): Unit =
    _intermediateMap = _intermediateMap.updated(path, intermediate ::: _intermediateMap(path))
    distribute(shuffles)

  private def splitEvents(events: List[WrappedEvent[_]]): Map[Int, List[WrappedEvent[_]]] =
    val suffix = events.filter(_.key.x == -1) // Error, Atom, Seal
    val prefix = events.filter(_.key.x != -1)
    Range(0, nPartitions)
      .map(i => i -> prefix.filter(x => safeModulo(x.key.x, nPartitions).toInt == i))
      .toMap
      .view
      .mapValues(_ ::: suffix)
      .toMap

  private def split(batch: EventBatch): Map[Int, EventBatch] =
    batch match
      case AtomBatch(path, events) =>
        splitEvents(events).view.mapValues(AtomBatch(path, _)).toMap
      case AskBatch(meta, events) =>
        splitEvents(events).view.mapValues(AskBatch(meta, _)).toMap
      case ReplyBatch(meta, events) =>
        splitEvents(events).view.mapValues(ReplyBatch(meta, _)).toMap
      case ShuffleBatch(path, task, events) =>
        splitEvents(events).view.mapValues(ShuffleBatch(path, task, _)).toMap

  private def distribute(outputs: List[EventBatch]): Unit =
    outputs.foreach: output =>
      split(output).foreach:
        case (partition, batch) =>
          cache.get(partition).get ! Shuffle(partition, batch)
