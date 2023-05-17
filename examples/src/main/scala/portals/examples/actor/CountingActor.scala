package portals.examples.actor

import scala.annotation.experimental

import portals.system.Systems

@experimental
object CountingActor:
  inline val COUNTING_N = 1024

  sealed trait CountingActorCommand
  @experimental case object Increment extends CountingActorCommand
  @experimental case class Retrieve(replyTo: ActorRef[ProducerActorCommand]) extends CountingActorCommand

  sealed trait ProducerActorCommand
  @experimental case class RetrieveReply(value: Int) extends ProducerActorCommand

  object CountingBehaviors:
    val countingBehavior: ActorBehavior[CountingActorCommand] =
      ActorBehaviors.init { ctx ?=>
        val count = ValueTypedActorState[Int]("count")

        ActorBehaviors.receive {
          case Increment =>
            count.set(count.get().getOrElse(0) + 1)
            ActorBehaviors.same

          case Retrieve(replyTo) =>
            ctx.send(replyTo)(RetrieveReply(count.get().getOrElse(0)))
            ActorBehaviors.stopped
        }
      }

    val producerBehavior: ActorBehavior[ProducerActorCommand] =
      ActorBehaviors.init { ctx ?=>
        val countingActor = ctx.create(countingBehavior)

        for (i <- 0 until COUNTING_N) do ctx.send(countingActor)(Increment)

        ctx.send(countingActor)(Retrieve(ctx.self))

        ActorBehaviors.receive { case RetrieveReply(value) =>
          ctx.log(s"CountingActor replied with $value")
          ActorBehaviors.stopped
        }
      }

@experimental
object CountingActorMain extends App:
  import portals.api.dsl.DSL.*
  import portals.api.dsl.ExperimentalDSL.*

  import ActorEvents.*
  import CountingActor.CountingBehaviors.*

  val config = ActorConfig.default
    .replace("logging", false)

  val app = PortalsApp("CountingActor") {
    val generator = Generators.signal[ActorMessage](ActorCreate(ActorRef.fresh(), producerBehavior))
    val wf = ActorWorkflow(generator.stream, config)
  }

  val system = Systems.test()
  system.launch(app)
  system.stepUntilComplete()
  system.shutdown()
