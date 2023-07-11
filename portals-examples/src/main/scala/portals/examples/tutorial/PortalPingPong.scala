package portals.examples.tutorial

import scala.annotation.experimental
import scala.concurrent.Await

import portals.api.dsl.DSL
import portals.api.dsl.ExperimentalDSL
import portals.system.Systems
import portals.util.Future

@experimental
@main def PortalPingPong(): Unit =
  import portals.api.dsl.DSL.*

  import portals.api.dsl.ExperimentalDSL.*

  val app = PortalsApp("PortalPingPong") {
    sealed trait PingPong
    case class Ping(x: Int) extends PingPong
    case class Pong(x: Int) extends PingPong

    val generator = Generators.fromList(List(1024 * 128))

    val portal = Portal[Ping, Pong]("portal")

    val replier = Workflows[Nothing, Nothing]("replier")
      .source(Generators.empty.stream)
      .replier[Nothing](portal) { _ => () } { case Ping(x) =>
        reply(Pong(x - 1))
      }
      .sink()
      .freeze()

    val asker = Workflows[Int, Int]("asker")
      .source(generator.stream)
      .recursiveAsker[Int](portal) { self => x =>
        val future: Future[Pong] = ask(portal)(Ping(x))
        await(future) {
          ctx.emit(future.value.get.x)
          if future.value.get.x > 0 then self(future.value.get.x)
        }
      }
      .filter(_ % 1024 == 0)
      .logger()
      .sink()
      .freeze()
  }

  val system = Systems.test()

  system.launch(app)

  system.stepUntilComplete()

  system.shutdown()
