package portals.runtime.parallel

import org.apache.pekko.actor.typed.scaladsl.AbstractBehavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior

import portals.application.Application
import portals.runtime.executor.TaskExecutorImpl
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

    /** Pekko Actor commands for this behavior. */
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

  extension (batch: EventBatch) {
    def path: Path = batch match
      case AtomBatch(path, _) => path
      case AskBatch(meta, _) => "ask" + meta.askingWF + meta.portal
      case ReplyBatch(meta, _) => "reply" + meta.askingWF + meta.portal
      case ShuffleBatch(path, _, _) => path
  }

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
  private var _alignMap: Map[Path, List[EventBatch]] = Map.empty.withDefaultValue(Nil)
  private var _alignState: Map[Path, Int] = Map.empty.withDefaultValue(0)
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
        this.alignedFeed(batch)
        Behaviors.same

  def launch(app: Application): Unit =
    interpreter.launch(app)

  /** Distribute the produced atom for a generator.
    *
    * FIXME: This is a hack as a generator only executes on a single partition.
    * A better solution would be to have the generator output be of a different
    * type (non-parallel), so that it could be fed directly by all receivers.
    */
  private def distributeForGenerators(outputs: List[EventBatch]): Unit =
    this.distribute(outputs)
    // we need to broadcast some empty atoms to all if it is a generator to trigger alignment
    Range(0, nPartitions - 1).foreach: i =>
      outputs.foreach: output =>
        output match
          case AtomBatch(path, _) =>
            this.distribute(List(AtomBatch(path, List(Atom))))
          case _ => ???

  def step(): Unit =
    if interpreter.canStep() then
      interpreter.step() match
        case Completed(path, outputs) =>
          if !path.contains("/generators/") then //
            this.distribute(outputs)
          else //
            distributeForGenerators(outputs)
        case Suspended(path, shuffles, intermediate) =>
          this.shuffle(path, shuffles, intermediate)

  // This should be a
  private val _texec = TaskExecutorImpl()

  // This may come to be an issue as a stream may seal early, and not all events are processed of a stream. To fix it, one
  /** Align atoms and consolidate them into larger atoms before feeding them.
    *
    * FIXME: This is an ugly hack, and shares much code with the `resume`
    * method. FIXME: The system does currently not align the event batches for
    * atoms, rather they are formed on a first-come first-serve basis. This may
    * come to be an issue as a stream may seal early, and not all events are
    * processed of a stream. To fix it, one would need to implement a proper
    * alignment algorithm which ensures that the alignment takes one atom per
    * sending partition to form the larger atoms. This is currently not the
    * case.
    */
  private def alignedFeed(batch: EventBatch) =
    _alignState = _alignState.updated(batch.path, _alignState(batch.path) + 1)
    _alignMap = _alignMap.updated(batch.path, batch :: _alignMap(batch.path))
    if _alignState(batch.path) == nPartitions then
      val batches = _alignMap(batch.path)
      _alignMap = _alignMap - batch.path
      _alignState = _alignState - batch.path
      val consolidatedBatch = batch match
        case AtomBatch(path, list) =>
          AtomBatch(path, _texec.clean_events(batches.flatMap(_.list)))
        case AskBatch(meta, list) =>
          AskBatch(meta, _texec.clean_events(batches.flatMap(_.list)))
        case ReplyBatch(meta, list) =>
          ReplyBatch(meta, _texec.clean_events(batches.flatMap(_.list)))
        case ShuffleBatch(path, task, list) =>
          ???
      this.feed(consolidatedBatch)

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
