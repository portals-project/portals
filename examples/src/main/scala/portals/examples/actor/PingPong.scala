package portals.examples.actor

import scala.annotation.experimental

import portals.api.dsl.DSL
import portals.api.dsl.ExperimentalDSL
import portals.system.Systems

@experimental
object PingPong:
  inline val PINGPONG_N = 1024

  case class PingPong(i: Int, replyTo: ActorRef[PingPong])

  object PingPongActors:
    val pingPongBehavior: ActorBehavior[PingPong] = ActorBehaviors.receive { ctx ?=> msg =>
      ctx.log(msg)
      msg match
        case PingPong(i, replyTo) =>
          i match
            case -1 =>
              ctx.log("PingPong finished")
              ActorBehaviors.stopped
            case 0 =>
              ctx.log("PingPong finished")
              ctx.send(replyTo)(PingPong(i - 1, ctx.self))
              ActorBehaviors.stopped
            case _ =>
              ctx.send(replyTo)(PingPong(i - 1, ctx.self))
              ActorBehaviors.same
    }

    val initBehavior: ActorBehavior[Unit] = ActorBehaviors.init { ctx ?=>
      val p1 = ctx.create(pingPongBehavior)
      val p2 = ctx.create(pingPongBehavior)
      ctx.send(p1)(PingPong(PINGPONG_N, p2))
      ActorBehaviors.stopped
    }

@experimental
object PingPongMain extends App:
  import portals.api.dsl.DSL.*
  import portals.api.dsl.ExperimentalDSL.*

  import ActorEvents.*
  import PingPong.PingPongActors.*

  val app = PortalsApp("PingPong") {
    val generator = Generators.signal[ActorMessage](ActorCreate(ActorRef.fresh(), initBehavior))
    val wf = ActorWorkflow(generator.stream)
  }

  /** synchronous interpreter */
  val system = Systems.interpreter()
  system.launch(app)
  system.stepUntilComplete()
  system.shutdown()

  // /** parallel runtime */
  // val system = Systems.parallel()
  // system.launch(app)
  // Thread.sleep(1000) // if using parallel
  // system.shutdown()