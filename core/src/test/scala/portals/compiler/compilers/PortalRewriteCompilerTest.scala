package portals.compiler.compilers

import scala.annotation.experimental

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test

import portals.api.dsl.DSL.*
import portals.application.*
import portals.application.task.*
import portals.compiler.CompilerBuilder
import portals.system.*
import portals.test.*
import portals.util.*

@RunWith(classOf[JUnit4])
class PortalTest:

  @Test
  @experimental
  def testPingPongRewriting(): Unit =
    import portals.api.dsl.DSL.*

    import portals.api.dsl.ExperimentalDSL.*

    val testData = List(List(1024))

    val testerReplier = new TestUtils.Tester[Int]()
    val testerAsker = new TestUtils.Tester[Int]()

    val app = PortalsApp("PortalPingPong") {
      sealed trait PingPong
      case class Ping(x: Int) extends PingPong
      case class Pong(x: Int) extends PingPong

      val generator = Generators.fromListOfLists(testData)

      val portal = Portal[Ping, Pong]("portal")

      val replier = Workflows[Nothing, Nothing]("replier")
        .source(Generators.empty.stream)
        // .portal
        .replier[Nothing](portal) { _ => () } { case Ping(x) =>
          testerReplier.enqueueEvent(x)
          reply(Pong(x - 1))
        }
        .sink()
        .freeze()

      val asker = Workflows[Int, Int]("asker")
        .source(generator.stream)
        // .portal
        .recursiveAsker[Int](portal) { self => x =>
          val future: Future[Pong] = ask(portal)(Ping(x))
          future.await {
            testerAsker.enqueueEvent(future.value.get.x)
            ctx.emit(future.value.get.x)
            if future.value.get.x > 0 then self(future.value.get.x)
          }
        }
        .filter(_ % 1024 == 0)
        // .logger()
        .sink()
        .freeze()
    }

    val system = Systems.test()

    val rewriteApp = CompilerBuilder
      .portalRewriteCompiler()
      .compile(app)
    system.launch(rewriteApp)

    system.stepUntilComplete()

    system.shutdown()

    Range(1024, 0, -1).foreach { x =>
      testerReplier.receiveAssert(x)
    }
    testerReplier.isEmptyAssert()

    Range(1024 - 1, 0 - 1, -1).foreach { x =>
      testerAsker.receiveAssert(x)
    }
    testerAsker.isEmptyAssert()

  @Test
  @experimental
  def testMultipleAskers(): Unit =
    import portals.api.dsl.DSL.*

    import portals.api.dsl.ExperimentalDSL.*

    sealed trait PingPong
    case class Ping(x: Int) extends PingPong
    case class Pong(x: Int) extends PingPong

    val testData = List(List(1024))

    val testerReplier = new TestUtils.Tester[Int]()
    val testerAsker1 = new TestUtils.Tester[Int]()
    val testerAsker2 = new TestUtils.Tester[Int]()

    val app = PortalsApp("PortalPingPong") {
      val generator = Generators.fromListOfLists(testData)

      val portal = Portal[Ping, Pong]("portal")

      val replier = Workflows[Nothing, Nothing]("replier")
        .source(Generators.empty.stream)
        .replier[Nothing](portal) { _ => () } { case Ping(x) =>
          testerReplier.enqueueEvent(x)
          reply(Pong(x - 1))
        }
        .sink()
        .freeze()

      val askersrc = Workflows[Int, Int]("asker")
        .source(generator.stream)

      val asker1 = askersrc
        .recursiveAsker[Int](portal) { self => x =>
          val future: Future[Pong] = ask(portal)(Ping(x))
          future.await {
            testerAsker1.enqueueEvent(future.value.get.x)
            ctx.emit(future.value.get.x)
            if future.value.get.x > 0 then self(future.value.get.x)
          }
        }
        .filter(_ % 1024 == 0)
        // .logger()
        .sink()

      val asker2 = askersrc
        .recursiveAsker[Int](portal) { self => x =>
          val future: Future[Pong] = ask(portal)(Ping(x))
          future.await {
            testerAsker2.enqueueEvent(future.value.get.x)
            ctx.emit(future.value.get.x)
            if future.value.get.x > 0 then self(future.value.get.x)
          }
        }
        .filter(_ % 1024 == 0)
        // .logger()
        .sink()
    }

    val system = Systems.test()

    val rewriteApp = CompilerBuilder
      .portalRewriteCompiler()
      .compile(app)
    system.launch(rewriteApp)

    system.stepUntilComplete()

    system.shutdown()

    Range(1024, 0, -1).foreach { x =>
      testerReplier.receiveAssert(x)
      testerReplier.receiveAssert(x)
    }
    testerReplier.isEmptyAssert()

    Range(1024 - 1, 0 - 1, -1).foreach { x =>
      testerAsker1.receiveAssert(x)
    }
    testerAsker1.isEmptyAssert()

    Range(1024 - 1, 0 - 1, -1).foreach { x =>
      testerAsker2.receiveAssert(x)
    }
    testerAsker2.isEmptyAssert()

  end testMultipleAskers // def
