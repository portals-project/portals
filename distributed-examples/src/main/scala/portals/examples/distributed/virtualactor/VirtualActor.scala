package portals.examples.distributed.virtualactor

import scala.annotation.experimental

import portals.*

// TODO: merge VirtualActor with Actor runtime once we have broadcast operations.

@experimental
sealed trait VirtualActorState:
  def get(key: Any)(using ctx: VirtualActorContext[_]): Option[Any]
  def set(key: Any, value: Any)(using ctx: VirtualActorContext[_]): Unit
  def del(key: Any)(using ctx: VirtualActorContext[_]): Unit
end VirtualActorState // trait

@experimental
private[portals] object VirtualActorState:
  def apply(): VirtualActorState =
    new VirtualActorState {
      override def get(key: Any)(using ctx: VirtualActorContext[_]): Option[Any] = ctx.tctx.state.get(key)
      override def set(key: Any, value: Any)(using ctx: VirtualActorContext[_]): Unit = ctx.tctx.state.set(key, value)
      override def del(key: Any)(using ctx: VirtualActorContext[_]): Unit = ctx.tctx.state.del(key)
    }
end VirtualActorState // object

@experimental
sealed trait ValueTypedVirtualActorState[T]:
  def get()(using ctx: VirtualActorContext[_]): Option[T]
  def set(value: T)(using ctx: VirtualActorContext[_]): Unit
  def del()(using ctx: VirtualActorContext[_]): Unit
end ValueTypedVirtualActorState // trait

@experimental
object ValueTypedVirtualActorState:
  def apply[T](name: String): ValueTypedVirtualActorState[T] =
    new ValueTypedVirtualActorState[T] {
      override def get()(using ctx: VirtualActorContext[_]): Option[T] = ctx.state.get(name).asInstanceOf[Option[T]]
      override def set(value: T)(using ctx: VirtualActorContext[_]): Unit = ctx.state.set(name, value)
      override def del()(using ctx: VirtualActorContext[_]): Unit = ctx.state.del(name)
    }
end ValueTypedVirtualActorState // object

@experimental
sealed trait MapTypedVirtualActorState[K, V]:
  def get(key: K)(using ctx: VirtualActorContext[_]): Option[V]
  def set(key: K, value: V)(using ctx: VirtualActorContext[_]): Unit
  def del(key: K)(using ctx: VirtualActorContext[_]): Unit
end MapTypedVirtualActorState // trait

@experimental
object MapTypedVirtualActorState:
  def apply[K, V](): MapTypedVirtualActorState[K, V] =
    new MapTypedVirtualActorState[K, V] {
      override def get(key: K)(using ctx: VirtualActorContext[_]): Option[V] =
        ctx.state.get(key).asInstanceOf[Option[V]]
      override def set(key: K, value: V)(using ctx: VirtualActorContext[_]): Unit = ctx.state.set(key, value)
      override def del(key: K)(using ctx: VirtualActorContext[_]): Unit = ctx.state.del(key)
    }
end MapTypedVirtualActorState // object

/** A reference to a virtual actor. */
@experimental
sealed trait VirtualActorRef[-T]:
  self =>

  /** the kind/type/name of the actor as a 64-bit integer, used to identify what virtual actor to execute */
  private[portals] val kind: Long

  /** the key/identifier of the virtual actor as a 64-bit integer, used to identify the physical instance */
  private[portals] val key: Long

  /** the hash value of the reference, used to route messages */
  private[portals] val hash: Long

  override def toString(): String = "ActorRef(" + kind + ", " + key + ")"
end VirtualActorRef // trait

@experimental
object VirtualActorRef:
  import java.util.UUID.randomUUID

  import scala.util.hashing.MurmurHash3

  def apply[T](_kind: Long, _key: Long): VirtualActorRef[T] = new VirtualActorRef[T] {
    override val kind: Long = _kind
    override val key: Long = _key
    override val hash: Long = _key ^ _kind
  }

  def fresh[T](kind: Long): VirtualActorRef[T] =
    val key = keyFromUUID()
    VirtualActorRef.apply[T](kind, key)

  def fresh[T](name: String): VirtualActorRef[T] =
    val kind = hashFromString(name)
    val key = keyFromUUID()
    VirtualActorRef.apply[T](kind, key)

  private def keyFromUUID(): Long =
    val uuid = randomUUID()
    val key = uuid.getLeastSignificantBits() ^ uuid.getMostSignificantBits()
    key

  private def hashFromString(s: String): Long =
    val h1 = MurmurHash3.stringHash(s)
    val h2 = MurmurHash3.stringHash(s, h1)
    val h3 = (h1.longValue() << 32) | (h2 & 0xffffffffL)
    h3
end VirtualActorRef // object

@experimental
sealed trait VirtualActorContext[T]:
  import VirtualActorEvents.*
  var self: VirtualActorRef[T]
  val state: VirtualActorState
  def log[U](msg: U): Unit
  def send[U](aref: VirtualActorRef[U])(msg: U): Unit
  private[portals] val tctx: TaskContext[VirtualActorMessage, VirtualActorMessage]
end VirtualActorContext // trait

@experimental
private[portals] object VirtualActorContext:
  import VirtualActorEvents.*

  def apply[T](ctx: TaskContext[VirtualActorMessage, VirtualActorMessage]): VirtualActorContext[T] =
    new VirtualActorContext[T] {
      var self: VirtualActorRef[T] = null
      override val state: VirtualActorState = VirtualActorState()
      override def log[U](msg: U): Unit = tctx.log.info(msg.toString())
      override def send[U](aref: VirtualActorRef[U])(msg: U): Unit = tctx.emit(VirtualActorSend[U](aref, msg))
      override private[portals] val tctx: TaskContext[VirtualActorMessage, VirtualActorMessage] = ctx
    }
end VirtualActorContext // object

@experimental
sealed trait VirtualActorBehavior[T]

@experimental
object VirtualActorBehaviors:
  def receive[T](f: VirtualActorContext[T] ?=> T => VirtualActorBehavior[T]) =
    ReceiveVirtualActorBehavior(ctx => f(using ctx))

  def init[T](f: VirtualActorContext[T] ?=> VirtualActorBehavior[T]) =
    InitBehavior[T](ctx => f(using ctx))

  def same[T]: VirtualActorBehavior[T] = SameBehavior.asInstanceOf[VirtualActorBehavior[T]]

  def stopped[T]: VirtualActorBehavior[T] = StoppedBehavior.asInstanceOf[VirtualActorBehavior[T]]

  @experimental
  private[portals] case class ReceiveVirtualActorBehavior[T](f: VirtualActorContext[T] => T => VirtualActorBehavior[T])
      extends VirtualActorBehavior[T]

  @experimental
  private[portals] case class InitBehavior[T](f: VirtualActorContext[T] => VirtualActorBehavior[T])
      extends VirtualActorBehavior[T]

  @experimental
  private[portals] case object SameBehavior extends VirtualActorBehavior[Any]

  @experimental
  private[portals] case object StoppedBehavior extends VirtualActorBehavior[Any]

  @experimental
  private[portals] case object NoBehavior extends VirtualActorBehavior[Any]

  private[portals] def prepareBehavior[T](
      behavior: VirtualActorBehavior[T],
      ctx: VirtualActorContext[T]
  ): VirtualActorBehavior[T] =
    def prepareBehaviorRec(behavior: VirtualActorBehavior[T], ctx: VirtualActorContext[T]): VirtualActorBehavior[T] = {
      behavior match
        case InitBehavior(initFactory) => prepareBehaviorRec(initFactory(ctx), ctx)
        case b @ ReceiveVirtualActorBehavior(_) => b
        case b @ StoppedBehavior => b
        case b @ SameBehavior => b
        case b @ NoBehavior => b
    }
    prepareBehaviorRec(behavior, ctx)
  end prepareBehavior // def
end VirtualActorBehaviors // object

@experimental
private[portals] object VirtualActorEvents:
  sealed trait VirtualActorMessage(val aref: VirtualActorRef[_])

  @experimental
  case class VirtualActorSend[T](override val aref: VirtualActorRef[T], msg: T) extends VirtualActorMessage(aref)

  // @experimental
  // case class VirtualActorCreate[T](override val aref: VirtualActorRef[T], behavior: VirtualActorBehavior[T])
  //     extends VirtualActorMessage(aref)
end VirtualActorEvents // object

@experimental
private[portals] object VirtualActorRuntime:
  import StashExtension.*
  import VirtualActorBehaviors.*
  import VirtualActorEvents.*

  def apply(behaviors: Map[Long, VirtualActorBehavior[Any]]): Task[VirtualActorMessage, VirtualActorMessage] =
    Tasks.init { ctx ?=>
      val behavior = PerKeyState[VirtualActorBehavior[Any]]("behavior", NoBehavior)
      val actx = VirtualActorContext[Any](ctx)

      Tasks.processor {
        case VirtualActorSend(aref, msg) => {
          actx.self = aref.asInstanceOf[VirtualActorRef[Any]]
          behavior.get() match
            case ReceiveVirtualActorBehavior(f) =>
              f(actx)(msg) match
                case b @ ReceiveVirtualActorBehavior(f) => behavior.set(b)
                case b @ StoppedBehavior => behavior.set(b)
                case SameBehavior => ()
                case InitBehavior(_) => ???
                case NoBehavior => ???
            case NoBehavior =>
              actx.self = aref.asInstanceOf[VirtualActorRef[Any]]
              prepareBehavior(behaviors.get(aref.kind).get, actx) match
                case b @ ReceiveVirtualActorBehavior(f) =>
                  f(actx)(msg) match
                    case b @ ReceiveVirtualActorBehavior(f) => behavior.set(b)
                    case b @ StoppedBehavior => behavior.set(b)
                    case SameBehavior => behavior.set(b)
                    case InitBehavior(_) => ???
                    case NoBehavior => ???
                case b @ StoppedBehavior => behavior.set(b)
                case InitBehavior(_) => ???
                case SameBehavior => ???
                case NoBehavior => ???
            case InitBehavior(_) => ???
            case SameBehavior => ???
            case StoppedBehavior => ???
        }
      }
    }

@experimental
object VirtualActorWorkflow:
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  import VirtualActorEvents.*

  def apply(stream: AtomicStreamRef[VirtualActorMessage])(behaviors: Map[Long, VirtualActorBehavior[Any]])(using
      ApplicationBuilder
  ): Workflow[VirtualActorMessage, VirtualActorMessage] =
    val sequencer = Sequencers.random[VirtualActorMessage]()

    val workflow = Workflows[VirtualActorMessage, VirtualActorMessage]("workflow")
      .source(sequencer.stream)
      .key(_.aref.key)
      .task(VirtualActorRuntime(behaviors))
      .sink()
      .freeze()

    val _ = Connections.connect(stream, sequencer)
    val _ = Connections.connect(workflow.stream, sequencer)

    workflow
