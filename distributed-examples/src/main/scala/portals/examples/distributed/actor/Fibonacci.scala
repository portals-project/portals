package portals.examples.distributed.actor

import scala.annotation.experimental

import portals.*
import portals.api.dsl.DSL
import portals.api.dsl.ExperimentalDSL

@experimental
object Fibonacci:
  sealed trait FibCommand
  @experimental case class Fib(replyTo: ActorRef[FibReply], i: Int) extends FibCommand
  @experimental case class FibReply(i: Int) extends FibCommand

  object FibActors:
    val fibBehavior: ActorBehavior[FibCommand] = ActorBehaviors.init[FibCommand] { ctx ?=>
      val fibValue = ValueTypedActorState[Int]("fibValue")
      val fibCount = ValueTypedActorState[Int]("fibCount")
      val fibReply = ValueTypedActorState[ActorRef[FibReply]]("fibReply")
      ActorBehaviors.receive {
        case Fib(replyTo, i) =>
          i match
            case 0 =>
              ctx.send(replyTo)(FibReply(0))
              ActorBehaviors.same
            case 1 =>
              ctx.send(replyTo)(FibReply(1))
              ActorBehaviors.same
            case n =>
              fibValue.set(0); fibCount.set(0); fibReply.set(replyTo)
              ctx.send(ctx.create(fibBehavior))(Fib(ctx.self, n - 1))
              ctx.send(ctx.create(fibBehavior))(Fib(ctx.self, n - 2))
              ActorBehaviors.same
        case FibReply(i) =>
          fibValue.set(fibValue.get().get + i)
          fibCount.set(fibCount.get().get + 1)
          fibCount.get().get match
            case 1 => ActorBehaviors.same
            case 2 =>
              ctx.send(fibReply.get().get)(FibReply(fibValue.get().get))
              ActorBehaviors.stopped
            case _ => ???
      }
    }

    def initBehavior(fib_n: Int): ActorBehavior[FibReply] = ActorBehaviors.init[FibReply] { ctx ?=>
      ctx.send(ctx.create[FibCommand](fibBehavior))(Fib(ctx.self, fib_n))
      ActorBehaviors.receive { msg =>
        ctx.log(msg)
        ActorBehaviors.stopped
      }
    }

@experimental
object FibonacciMain extends App:
  import portals.api.dsl.DSL.*
  import portals.api.dsl.ExperimentalDSL.*

  import ActorEvents.*
  import Fibonacci.FibActors.initBehavior

  val FIB_N = 21

  val app = PortalsApp("Fibonacci") {
    val generator = Generators.signal[ActorMessage](ActorCreate(ActorRef.fresh(), initBehavior(FIB_N)))
    val wf = ActorWorkflow(generator.stream)
  }

  /** synchronous interpreter */
  val system = Systems.test()
  system.launch(app)
  system.stepUntilComplete()
  system.shutdown()
