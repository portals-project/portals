package portals.examples

import scala.annotation.experimental

import portals.*

@experimental
@main def PortalPingPong(): Unit =
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  val app = PortalsApp("PortalPingPong") {
    sealed trait PingPong
    case class Ping(x: Int) extends PingPong
    case class Pong(x: Int) extends PingPong

    val generator = Generators.fromList(List(128))

    val portal = Portal[Ping, Pong]("portal")

    val replier = Workflows[Nothing, Nothing]("replier")
      .source(Generators.empty.stream)
      .portal(portal)
      .replier[Nothing] { _ => () } { case Ping(x) => reply(Pong(x - 1)) }
      .sink()
      .freeze()

    val asker = Workflows[Int, Int]("asker")
      .source(generator.stream)
      .portal(portal)
      .askerz[Int] { self => x =>
        val future = ctx.ask(portal)(Ping(x))
        future.await {
          ctx.emit(future.value.get.x)
          if future.value.get.x < 0 then Tasks.same
          else self(x - 1); Tasks.same
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
