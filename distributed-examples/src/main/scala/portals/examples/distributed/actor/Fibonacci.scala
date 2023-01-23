package portals.examples.distributed.actor

import scala.annotation.experimental

import portals.*

@experimental
object Fibonacci:
  inline val FIB_N = 21

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

    val initBehavior: ActorBehavior[FibReply] = ActorBehaviors.init[FibReply] { ctx ?=>
      ctx.send(ctx.create[FibCommand](fibBehavior))(Fib(ctx.self, FIB_N))
      ActorBehaviors.receive { msg =>
        ctx.log(msg)
        ActorBehaviors.stopped
      }
    }

@experimental
object FibonacciMain extends App:
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  import ActorEvents.*
  import Fibonacci.FibActors.initBehavior

  val app = PortalsApp("Fibonacci") {
    val generator = Generators.signal[ActorMessage](ActorCreate(ActorRef.fresh(), initBehavior))
    val wf = ActorWorkflow(generator.stream)
  }

  /** synchronous interpreter */
  val system = Systems.test()
  system.launch(app)
  system.stepUntilComplete()
  system.shutdown()

  // /** parallel runtime */
  // val system = Systems.parallel()
  // system.launch(app)
  // Thread.sleep(1000) // if using parallel
  // system.shutdown()
