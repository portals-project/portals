package portals.examples.distributed.actor

import scala.annotation.experimental

import portals.*
import portals.api.builder.ApplicationBuilder
import portals.api.builder.TaskBuilder
import portals.application.task.PerKeyState

import portals.api.dsl.DSL
import portals.api.dsl.ExperimentalDSL
@experimental
sealed trait ActorState:
  def get(key: Any)(using ctx: ActorContext[_]): Option[Any]
  def set(key: Any, value: Any)(using ctx: ActorContext[_]): Unit
  def del(key: Any)(using ctx: ActorContext[_]): Unit
end ActorState // trait

@experimental
private[portals] object ActorState:
  def apply(): ActorState =
    new ActorState {
      override def get(key: Any)(using ctx: ActorContext[_]): Option[Any] = ctx.tctx.state.get(key)
      override def set(key: Any, value: Any)(using ctx: ActorContext[_]): Unit = ctx.tctx.state.set(key, value)
      override def del(key: Any)(using ctx: ActorContext[_]): Unit = ctx.tctx.state.del(key)
    }
end ActorState // object

@experimental
sealed trait ValueTypedActorState[T]:
  def get()(using ctx: ActorContext[_]): Option[T]
  def set(value: T)(using ctx: ActorContext[_]): Unit
  def del()(using ctx: ActorContext[_]): Unit
end ValueTypedActorState // trait

@experimental
object ValueTypedActorState:
  def apply[T](name: String): ValueTypedActorState[T] =
    new ValueTypedActorState[T] {
      override def get()(using ctx: ActorContext[_]): Option[T] = ctx.state.get(name).asInstanceOf[Option[T]]
      override def set(value: T)(using ctx: ActorContext[_]): Unit = ctx.state.set(name, value)
      override def del()(using ctx: ActorContext[_]): Unit = ctx.state.del(name)
    }
end ValueTypedActorState // object

@experimental
sealed trait MapTypedActorState[K, V]:
  def get(key: K)(using ctx: ActorContext[_]): Option[V]
  def set(key: K, value: V)(using ctx: ActorContext[_]): Unit
  def del(key: K)(using ctx: ActorContext[_]): Unit
end MapTypedActorState // trait

@experimental
object MapTypedActorState:
  def apply[K, V](): MapTypedActorState[K, V] =
    new MapTypedActorState[K, V] {
      override def get(key: K)(using ctx: ActorContext[_]): Option[V] = ctx.state.get(key).asInstanceOf[Option[V]]
      override def set(key: K, value: V)(using ctx: ActorContext[_]): Unit = ctx.state.set(key, value)
      override def del(key: K)(using ctx: ActorContext[_]): Unit = ctx.state.del(key)
    }
end MapTypedActorState // object

@experimental
sealed trait ActorRef[-T]:
  private[portals] val key: Long
  override def toString(): String = "ActorRef(" + key + ")"
end ActorRef // trait

@experimental
object ActorRef:
  import java.util.UUID.randomUUID

  def apply[T](_key: Long): ActorRef[T] = new ActorRef[T] {
    override val key: Long = _key
  }

  def fresh[T](): ActorRef[T] =
    val uuid = randomUUID()
    val key = uuid.getLeastSignificantBits() ^ uuid.getMostSignificantBits()
    ActorRef[T](key)
end ActorRef // object

@experimental
sealed trait ActorContext[T]:
  import ActorEvents.*
  var self: ActorRef[T]
  val state: ActorState
  def log[U](msg: U): Unit
  def send[U](aref: ActorRef[U])(msg: U): Unit
  def create[U](behavior: ActorBehavior[U]): ActorRef[U]
  private[portals] val tctx: ProcessorTaskContext[ActorMessage, ActorMessage]
end ActorContext // trait

@experimental
private[portals] object ActorContext:
  import ActorEvents.*

  def apply[T](ctx: ProcessorTaskContext[ActorMessage, ActorMessage]): ActorContext[T] =
    new ActorContext[T] {
      var self: ActorRef[T] = null
      override val state: ActorState = ActorState()
      override def log[U](msg: U): Unit = tctx.log.info(msg.toString())
      override def send[U](aref: ActorRef[U])(msg: U): Unit = tctx.emit(ActorSend[U](aref, msg))
      override def create[U](behavior: ActorBehavior[U]): ActorRef[U] = {
        val aref = ActorRef.fresh[U]()
        tctx.emit(ActorCreate[U](aref, behavior))
        aref
      }
      override private[portals] val tctx: ProcessorTaskContext[ActorMessage, ActorMessage] = ctx
    }
end ActorContext // object

@experimental
sealed trait ActorBehavior[T]

@experimental
object ActorBehaviors:
  def receive[T](f: ActorContext[T] ?=> T => ActorBehavior[T]) =
    ReceiveActorBehavior(ctx => f(using ctx))

  def init[T](f: ActorContext[T] ?=> ActorBehavior[T]) =
    InitBehavior[T](ctx => f(using ctx))

  def same[T]: ActorBehavior[T] = SameBehavior.asInstanceOf[ActorBehavior[T]]

  def stopped[T]: ActorBehavior[T] = StoppedBehavior.asInstanceOf[ActorBehavior[T]]

  @experimental
  private[portals] case class ReceiveActorBehavior[T](f: ActorContext[T] => T => ActorBehavior[T])
      extends ActorBehavior[T]

  @experimental
  private[portals] case class InitBehavior[T](f: ActorContext[T] => ActorBehavior[T]) extends ActorBehavior[T]

  @experimental
  private[portals] case object SameBehavior extends ActorBehavior[Any]

  @experimental
  private[portals] case object StoppedBehavior extends ActorBehavior[Any]

  @experimental
  private[portals] case object NoBehavior extends ActorBehavior[Any]

  private[portals] def prepareBehavior[T](behavior: ActorBehavior[T], ctx: ActorContext[T]): ActorBehavior[T] =
    def prepareBehaviorRec(behavior: ActorBehavior[T], ctx: ActorContext[T]): ActorBehavior[T] = {
      behavior match
        case InitBehavior(initFactory) => prepareBehaviorRec(initFactory(ctx), ctx)
        case b @ ReceiveActorBehavior(_) => b
        case b @ StoppedBehavior => b
        case b @ SameBehavior => b
        case b @ NoBehavior => b
    }
    prepareBehaviorRec(behavior, ctx)
  end prepareBehavior // def
end ActorBehaviors // object

@experimental
private[portals] object ActorEvents:
  sealed trait ActorMessage(val aref: ActorRef[_])

  @experimental
  case class ActorSend[T](override val aref: ActorRef[T], msg: T) extends ActorMessage(aref)

  @experimental
  case class ActorCreate[T](override val aref: ActorRef[T], behavior: ActorBehavior[T]) extends ActorMessage(aref)
end ActorEvents // object

@experimental
private[portals] object ActorRuntime:
  import portals.api.builder.StashExtension.*

  import ActorBehaviors.*
  import ActorEvents.*

  def apply(): Task[ActorMessage, ActorMessage] =
    InitTask { ctx =>
      given TaskContextImpl[ActorMessage, ActorMessage, Nothing, Nothing] = ctx
      val behavior = PerKeyState[ActorBehavior[Any]]("behavior", NoBehavior)
      val actx = ActorContext[Any](ctx)
      TaskBuilder.stash { stash => // stash messages if behavior not yet created
        TaskBuilder.processor {
          case ActorSend(aref, msg) => {
            actx.self = aref.asInstanceOf[ActorRef[Any]]
            behavior.get() match
              case NoBehavior =>
                stash.stash(ActorSend(aref, msg))
              case ReceiveActorBehavior(f) =>
                f(actx)(msg) match
                  case b @ ReceiveActorBehavior(f) => behavior.set(b)
                  case b @ StoppedBehavior => behavior.set(b)
                  case SameBehavior => ()
                  case InitBehavior(_) => ???
                  case NoBehavior => ???
              case InitBehavior(_) => ???
              case SameBehavior => ???
              case StoppedBehavior => ???
          }
          case ActorCreate(aref, newBehavior) => {
            actx.self = aref.asInstanceOf[ActorRef[Any]]
            prepareBehavior(newBehavior.asInstanceOf[ActorBehavior[Any]], actx) match
              case b @ ReceiveActorBehavior(_) => behavior.set(b)
              case b @ StoppedBehavior => behavior.set(b)
              case InitBehavior(_) => ???
              case SameBehavior => ???
              case NoBehavior => ???
            if stash.size() > 0 then stash.unstashAll()
          }
        }
      }
    }

@experimental
object ActorWorkflow:
  import portals.api.dsl.DSL.*
  import portals.api.dsl.ExperimentalDSL.*

  import ActorEvents.*

  def apply(stream: AtomicStreamRef[ActorMessage])(using ApplicationBuilder): Workflow[ActorMessage, ActorMessage] =
    val sequencer = Sequencers.random[ActorMessage]()

    val workflow = Workflows[ActorMessage, ActorMessage]("workflow")
      .source(sequencer.stream)
      .key(_.aref.key)
      .task(ActorRuntime())
      .sink()
      .freeze()

    val _ = Connections.connect(stream, sequencer)
    val _ = Connections.connect(workflow.stream, sequencer)

    workflow
