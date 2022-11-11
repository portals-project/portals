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
        emit(x); reply(x - 1)
      }
      .logger()
      .sink()
      .freeze()

    val asker = Workflows[Int, Int]("asker")
      .source(generator.stream)
      .portal[Int, Int](portal)
      .asker[Int] { x =>
        val future = portal.ask(x)
        ctx.log.info(future.toString())
        future.await {
          emit(future.value.get)
          Tasks.same
        }
      }
      .logger()
      .sink()
      .freeze()
  }

  val system = Systems.test()

  system.launch(app)

  system.stepUntilComplete()

  system.shutdown()
