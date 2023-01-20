package portals.examples.distributed.actor

import java.util.UUID.randomUUID

import scala.annotation.experimental

import portals.*
import portals.examples.distributed.actor.Actors.ActorBehaviors.InitBehavior
import portals.examples.distributed.actor.Actors.ActorBehaviors.ReceiveActorBehavior

object Actors:
  trait ActorState:
    def get(key: Any): Any
    def set(key: Any, value: Any): Unit
    def del(key: Any): Unit

  object ActorStates:
    def apply(state: TaskState[Any, Any]): ActorState =
      new ActorState {
        override def get(key: Any): Any = state.get(key).get
        override def set(key: Any, value: Any): Unit = state.set(key, value)
        override def del(key: Any): Unit = state.del(key)
      }

  trait ActorRef[-T]:
    val key: String

  object ActorRefs:
    def apply[T](_key: String): ActorRef[T] = new ActorRef[T] { override val key: String = _key }
    def fresh[T](): ActorRef[T] = ActorRefs[T](randomUUID().toString())

  trait ActorContext[T]:
    var self: ActorRef[T]
    def state: ActorState
    def log[U](msg: U): Unit
    def send[U](aref: ActorRef[U])(msg: U): Unit
    def create[U](behavior: ActorBehavior[U]): ActorRef[U]

  object ActorContexts:
    def apply[T](ctx: TaskContext[ActorMessage, ActorMessage]): ActorContext[T] =
      new ActorContext[T] {
        var self: ActorRef[T] = null
        override def state: ActorState = ActorStates(ctx.state)
        override def log[U](msg: U): Unit = ctx.log.info(msg.toString())
        override def send[U](aref: ActorRef[U])(msg: U): Unit = ctx.emit(ActorSend[U](aref, msg))
        override def create[U](behavior: ActorBehavior[U]): ActorRef[U] = {
          val aref = ActorRefs.fresh[U]()
          ctx.emit(ActorCreate[U](aref, behavior))
          aref
        }
      }

  sealed trait ActorBehavior[T]:
    def receive(using context: ActorContext[T])(msg: T): Unit

  object ActorBehaviors:
    def receive[T](f: ActorContext[T] ?=> T => Unit) =
      ReceiveActorBehavior(ctx => f(using ctx))

    def init[T](f: ActorContext[T] ?=> ActorBehavior[T]) =
      InitBehavior[T](ctx => f(using ctx))

    def same[T]: ActorBehavior[T] = new ActorBehavior[T] {
      override def receive(using context: ActorContext[T])(msg: T): Unit = ???
    }

    case class ReceiveActorBehavior[T](f: ActorContext[T] => T => Unit) extends ActorBehavior[T]:
      override def receive(using context: ActorContext[T])(msg: T): Unit = f(context)(msg)

    case class InitBehavior[T](f: ActorContext[T] => ActorBehavior[T]) extends ActorBehavior[T]:
      override def receive(using context: ActorContext[T])(msg: T): Unit = ???

  sealed trait ActorMessage(val aref: ActorRef[_])
  case class ActorSend[T](override val aref: ActorRef[T], msg: T) extends ActorMessage(aref)
  case class ActorCreate[T](override val aref: ActorRef[T], behavior: ActorBehavior[T]) extends ActorMessage(aref)

  object ActorRuntimes:
    def basic(): Task[ActorMessage, ActorMessage] =
      Tasks.init { ctx ?=>
        val behavior = PerKeyState[ActorBehavior[Any]]("behavior", null)
        val actx = ActorContexts[Any](ctx)
        Tasks.processor { msg =>
          msg match {
            case ActorSend(aref, msg) => {
              actx.self = aref.asInstanceOf
              behavior.get().receive(using actx)(msg)
            }
            case ActorCreate(aref, newBehavior) => {
              actx.self = aref.asInstanceOf
              newBehavior match
                case InitBehavior(f) =>
                  val initializedBehavior = f(actx.asInstanceOf)
                  behavior.set(initializedBehavior.asInstanceOf)
                case _ =>
                  behavior.set(newBehavior.asInstanceOf)
            }
          }
        }
      }

@experimental
@main def main(): Unit =
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  import Actors.*

  object MyActors:
    sealed trait FibCommand
    case class Fib(replyTo: ActorRef[FibReply], i: Int) extends FibCommand
    case class FibReply(i: Int) extends FibCommand
    val fib: ActorBehavior[FibCommand] = ActorBehaviors.init[FibCommand] { ctx ?=>
      ActorBehaviors.receive { msg =>
        msg match
          case Fib(replyTo, i) =>
            i match
              case 0 => ctx.send(replyTo)(FibReply(0))
              case 1 => ctx.send(replyTo)(FibReply(1))
              case n =>
                ctx.send(ctx.create(fib))(Fib(ctx.self, n - 1))
                ctx.send(ctx.create(fib))(Fib(ctx.self, n - 2))
                ctx.state.set(0, 0)
                ctx.state.set(1, replyTo)
                ctx.state.set(2, 0)
          case FibReply(i) =>
            ctx.state.set(0, ctx.state.get(0).asInstanceOf[Int] + 1)
            ctx.state.set(2, ctx.state.get(2).asInstanceOf[Int] + i)
            if ctx.state.get(0).asInstanceOf[Int] == 2 then
              val x = ctx.state.get(2).asInstanceOf[Int]
              ctx.send(ctx.state.get(1).asInstanceOf[ActorRef[FibCommand]])(FibReply(ctx.state.get(2).asInstanceOf))
      }
    }

    val fibInit: ActorBehavior[FibReply] = ActorBehaviors.init[FibReply] { ctx ?=>
      ctx.send(ctx.create[FibCommand](fib))(Fib(ctx.self, 14))
      ActorBehaviors.receive { msg =>
        ctx.log(msg)
      }
    }

  import MyActors.*

  val app = PortalsApp("Actor") {

    // val generator = Generators.signal[ActorMessage]()

    val generator = Generators.signal[ActorMessage](ActorCreate(ActorRefs.fresh(), fibInit))

    val sequencer = Sequencers.random[ActorMessage]()

    val workflow = Workflows[ActorMessage, ActorMessage]("workflow")
      .source(sequencer.stream)
      .key(_.aref.key.hashCode())
      .logger()
      .task(ActorRuntimes.basic())
      .sink()
      .freeze()

    val _ = Connections.connect(generator.stream, sequencer)
    val _ = Connections.connect(workflow.stream, sequencer)
  }

  val system = Systems.test()

  system.launch(app)

  system.stepUntilComplete()

  system.shutdown()
