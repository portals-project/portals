package portals.examples

import scala.annotation.experimental

import portals.*

@experimental
@main def PortalPingPong(): Unit =
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  val app = PortalsApp("PortalPingPong") {
    val generator = Generators.fromList(List(128))

    val portal = Portal[Int, Int]("portal")

    val replier = Workflows[Int, Int]("replier")
      .source(Generators.empty.stream)
      .portal[Int, Int](portal)
      .replier { x =>
        emit(x)
      } { x =>
        emit(x)
        reply(x - 1)
      }
      .sink()
      .freeze()

    // TODO: there should be a nice way to do recursive task definitions from the askertask.
    def recursiveAskingBehavior(x: Int)(using AskerTaskContext[Int, Int, Int, Int]): Task[Int, Int] =
      val future = portal.ask(x)
      future.await {
        ctx.emit(future.value.get)
        if future.value.get < 0 then Tasks.same
        else recursiveAskingBehavior(x - 1)
      }

    val asker = Workflows[Int, Int]("asker")
      .source(generator.stream)
      .portal[Int, Int](portal)
      .asker[Int] { x => recursiveAskingBehavior(x) }
      .logger()
      .sink()
      .freeze()
  }

  val system = Systems.test()

  system.launch(app)

  system.stepUntilComplete()

  system.shutdown()
