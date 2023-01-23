package portals.examples.distributed.actor

import scala.annotation.experimental

import portals.*

@experimental
sealed trait ActorState:
  def get(key: Any): Option[Any]
  def set(key: Any, value: Any): Unit
  def del(key: Any): Unit

@experimental
private[portals] object ActorState:
  def apply(state: TaskState[Any, Any]): ActorState =
    new ActorState {
      override def get(key: Any): Option[Any] = state.get(key)
      override def set(key: Any, value: Any): Unit = state.set(key, value)
      override def del(key: Any): Unit = state.del(key)
    }

@experimental
sealed trait ValueTypedActorState[T]:
  def get(): Option[T]
  def set(value: T): Unit
  def del(): Unit

@experimental
object ValueTypedActorState:
  def apply[T](name: String)(using ctx: ActorContext[_]): ValueTypedActorState[T] =
    new ValueTypedActorState[T] {
      def get(): Option[T] = ctx.state.get(name).asInstanceOf[Option[T]]
      def set(value: T): Unit = ctx.state.set(name, value)
      def del(): Unit = ctx.state.del(name)
    }

@experimental
sealed trait MapTypedActorState[K, V]:
  def get(key: K): Option[V]
  def set(key: K, value: V): Unit
  def del(key: K): Unit

@experimental
object MapTypedActorState:
  def apply[K, V]()(using ctx: ActorContext[_]): MapTypedActorState[K, V] =
    new MapTypedActorState[K, V] {
      def get(key: K): Option[V] = ctx.state.get(key).asInstanceOf[Option[V]]
      def set(key: K, value: V): Unit = ctx.state.set(key, value)
      def del(key: K): Unit = ctx.state.del(key)
    }

@experimental
sealed trait ActorRef[-T]:
  val key: String
  override def toString(): String = "ActorRef(" + key + ")"

@experimental
object ActorRef:
  import java.util.UUID.randomUUID

  def apply[T](_key: String): ActorRef[T] = new ActorRef[T] { override val key: String = _key }
  def fresh[T](): ActorRef[T] = ActorRef[T](randomUUID().toString())

@experimental
sealed trait ActorContext[T]:
  var self: ActorRef[T]
  def state: ActorState
  def log[U](msg: U): Unit
  def send[U](aref: ActorRef[U])(msg: U): Unit
  def create[U](behavior: ActorBehavior[U]): ActorRef[U]

@experimental
private[portals] object ActorContext:
  import ActorEvents.*

  def apply[T](ctx: TaskContext[ActorMessage, ActorMessage]): ActorContext[T] =
    new ActorContext[T] {
      var self: ActorRef[T] = null
      override def state: ActorState = ActorState(ctx.state)
      override def log[U](msg: U): Unit = ctx.log.info(msg.toString())
      override def send[U](aref: ActorRef[U])(msg: U): Unit = ctx.emit(ActorSend[U](aref, msg))
      override def create[U](behavior: ActorBehavior[U]): ActorRef[U] = {
        val aref = ActorRef.fresh[U]()
        ctx.emit(ActorCreate[U](aref, behavior))
        aref
      }
    }

@experimental
sealed trait ActorBehavior[T]:
  def receive(using context: ActorContext[T])(msg: T): ActorBehavior[T]

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
      extends ActorBehavior[T]:
    override def receive(using context: ActorContext[T])(msg: T): ActorBehavior[T] = f(context)(msg)

  @experimental
  private[portals] case class InitBehavior[T](f: ActorContext[T] => ActorBehavior[T]) extends ActorBehavior[T]:
    override def receive(using context: ActorContext[T])(msg: T): ActorBehavior[T] = ???

  @experimental
  private[portals] case object SameBehavior extends ActorBehavior[Any]:
    override def receive(using context: ActorContext[Any])(msg: Any): ActorBehavior[Any] = ???

  @experimental
  private[portals] case object StoppedBehavior extends ActorBehavior[Any]:
    override def receive(using context: ActorContext[Any])(msg: Any): ActorBehavior[Any] = ???

@experimental
private[portals] object ActorEvents:
  sealed trait ActorMessage(val aref: ActorRef[_])

  @experimental
  case class ActorSend[T](override val aref: ActorRef[T], msg: T) extends ActorMessage(aref)

  @experimental
  case class ActorCreate[T](override val aref: ActorRef[T], behavior: ActorBehavior[T]) extends ActorMessage(aref)

@experimental
private[portals] object ActorRuntime:
  import ActorBehaviors.*
  import ActorEvents.*
  import StashExtension.*

  def apply(): Task[ActorMessage, ActorMessage] =
    Tasks.init { ctx ?=>
      val behavior = PerKeyState[ActorBehavior[Any]]("behavior", null)
      val actx = ActorContext[Any](ctx)

      // FIXME: stash not working yet; make sure stash logic works here
      Tasks.stash { stash => // stash messages if behavior not yet created
        Tasks.processor {
          case ActorSend(aref, msg) => {
            actx.self = aref.asInstanceOf[ActorRef[Any]]
            behavior.get() match
              case null =>
                stash.stash(ActorSend(aref, msg))
              case b =>
                b.receive(using actx)(msg) match
                  case InitBehavior(f) => ???
                  case newBehavior @ ReceiveActorBehavior(f) => behavior.set(newBehavior)
                  case SameBehavior => ()
                  case StoppedBehavior => behavior.del()
          }
          case ActorCreate(aref, newBehavior) => {
            actx.self = aref.asInstanceOf[ActorRef[Any]]
            newBehavior match
              case InitBehavior(f) =>
                val initializedBehavior = f(actx.asInstanceOf)
                behavior.set(initializedBehavior.asInstanceOf)
              case ReceiveActorBehavior(_) =>
                behavior.set(newBehavior.asInstanceOf)
              case SameBehavior => ???
              case StoppedBehavior => behavior.del()
            if stash.size() > 0 then stash.unstashAll()
          }
        }
      }
    }

@experimental
object ActorWorkflow:
  import scala.util.hashing.MurmurHash3

  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  import ActorEvents.*

  def apply(stream: AtomicStreamRef[ActorMessage])(using ApplicationBuilder): Workflow[ActorMessage, ActorMessage] =
    val sequencer = Sequencers.random[ActorMessage]()

    val workflow = Workflows[ActorMessage, ActorMessage]("workflow")
      .source(sequencer.stream)
      .key(x =>
        val h1 = MurmurHash3.stringHash(x.aref.key)
        val h2 = MurmurHash3.stringHash(x.aref.key, h1)
        val h3 = (h1.longValue() << 32) | (h2 & 0xffffffffL)
        h3
      )
      .task(ActorRuntime())
      .sink()
      .freeze()

    val _ = Connections.connect(stream, sequencer)
    val _ = Connections.connect(workflow.stream, sequencer)

    workflow
