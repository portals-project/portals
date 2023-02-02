package portals.examples.distributed.virtualactor

import scala.annotation.experimental

import portals.*

@experimental
object VirtualFibonacci:
  inline val FIB_N = 100

  sealed trait FibCommand
  @experimental case class Fib(replyTo: VirtualActorRef[FibReply], i: Int) extends FibCommand
  @experimental case class FibReply(i: BigInt) extends FibCommand
  @experimental case class FibInit(i: Int) extends FibCommand

  object FibVirtualActors:
    val KIND: Long = 42

    val fibBehavior: VirtualActorBehavior[FibCommand] = VirtualActorBehaviors.init[FibCommand] { ctx ?=>
      val fibValue = ValueTypedVirtualActorState[BigInt]("fibValue")
      val fibCount = ValueTypedVirtualActorState[Int]("fibCount")
      val fibReply = ValueTypedVirtualActorState[Set[VirtualActorRef[FibReply]]]("fibReply")

      VirtualActorBehaviors.receive {
        case FibInit(i) =>
          ctx.send(VirtualActorRef(KIND, i))(Fib(ctx.self, i))
          VirtualActorBehaviors.receive { msg =>
            ctx.log(msg)
            VirtualActorBehaviors.stopped
          }
        case Fib(replyTo, i) =>
          i match
            case 0 =>
              ctx.send(replyTo)(FibReply(0))
              VirtualActorBehaviors.same
            case 1 =>
              ctx.send(replyTo)(FibReply(1))
              VirtualActorBehaviors.same
            case n =>
              fibCount.get().getOrElse(0) match
                case 0 =>
                  fibReply.get() match
                    case None =>
                      fibValue.set(0); fibCount.set(0); fibReply.set(Set(replyTo))
                      ctx.send(VirtualActorRef(KIND, n - 1))(Fib(ctx.self, n - 1))
                      ctx.send(VirtualActorRef(KIND, n - 2))(Fib(ctx.self, n - 2))
                      VirtualActorBehaviors.same
                    case Some(replySet) =>
                      fibReply.set(replySet + replyTo)
                      VirtualActorBehaviors.same
                case 1 =>
                  fibReply.set(fibReply.get().getOrElse(Set.empty) + replyTo)
                  VirtualActorBehaviors.same
                case 2 =>
                  ctx.send(replyTo)(FibReply(fibValue.get().get))
                  VirtualActorBehaviors.same
                case _ => ???
        case FibReply(i) =>
          fibValue.set(fibValue.get().get + i)
          fibCount.set(fibCount.get().get + 1)
          fibCount.get().get match
            case 1 => VirtualActorBehaviors.same
            case 2 =>
              fibReply.get().get.foreach { replyTo => ctx.send(replyTo)(FibReply(fibValue.get().get)) }
              fibReply.set(Set.empty)
              VirtualActorBehaviors.same
            case _ => ???
      }
    }

    val behaviors: Map[Long, VirtualActorBehavior[Any]] = Map(
      KIND -> fibBehavior.asInstanceOf[VirtualActorBehavior[Any]]
    )

@experimental
object FibonacciMain extends App:
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  import VirtualActorEvents.*
  import VirtualFibonacci.*
  import VirtualFibonacci.FibVirtualActors.*

  val app = PortalsApp("Fibonacci") {
    val generator =
      Generators.signal[VirtualActorMessage](VirtualActorSend(VirtualActorRef.fresh(KIND), FibInit(FIB_N)))
    val wf = VirtualActorWorkflow(generator.stream)(behaviors)
  }

  /** synchronous interpreter */
  val system = Systems.test()
  system.launch(app)
  system.stepUntilComplete()
  system.shutdown()
