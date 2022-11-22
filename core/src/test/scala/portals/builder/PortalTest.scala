package portals

import scala.annotation.experimental

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test

import portals.test.*

@RunWith(classOf[JUnit4])
class PortalTest:

  @Test
  @experimental
  def testPingPong(): Unit =
    import portals.DSL.*
    import portals.DSL.BuilderDSL.*
    import portals.DSL.ExperimentalDSL.*

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
        .portal(portal)
        .replier[Nothing] { _ => () } { case Ping(x) =>
          testerReplier.enqueueEvent(x)
          reply(Pong(x - 1))
        }
        .sink()
        .freeze()

      val asker = Workflows[Int, Int]("asker")
        .source(generator.stream)
        .portal(portal)
        .askerRec[Int] { self => x =>
          val future: Future[Pong] = portal.ask(Ping(x))
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

    system.launch(app)

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

  end testPingPong // def

  @Test
  @experimental
  def testMultipleAskers(): Unit =
    import portals.DSL.*
    import portals.DSL.BuilderDSL.*
    import portals.DSL.ExperimentalDSL.*

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
        .portal(portal)
        .replier[Nothing] { _ => () } { case Ping(x) =>
          testerReplier.enqueueEvent(x)
          reply(Pong(x - 1))
        }
        .sink()
        .freeze()

      val askersrc = Workflows[Int, Int]("asker")
        .source(generator.stream)

      val asker1 = askersrc
        .portal(portal)
        .askerRec[Int] { self => x =>
          val future: Future[Pong] = portal.ask(Ping(x))
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
        .portal(portal)
        .askerRec[Int] { self => x =>
          val future: Future[Pong] = portal.ask(Ping(x))
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

    system.launch(app)

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

  @Test
  @experimental
  def testMultiplePortals(): Unit =
    import portals.DSL.*
    import portals.DSL.BuilderDSL.*
    import portals.DSL.ExperimentalDSL.*

    sealed trait PingPong
    case class Ping(x: Int) extends PingPong
    case class Pong(x: Int) extends PingPong

    val testData = List(List(1024))

    val testerReplier1 = new TestUtils.Tester[Int]()
    val testerReplier2 = new TestUtils.Tester[Int]()
    val testerAsker1 = new TestUtils.Tester[Int]()
    val testerAsker2 = new TestUtils.Tester[Int]()

    val app = PortalsApp("PortalPingPong") {
      val generator = Generators.fromListOfLists(testData)

      val portal1 = Portal[Ping, Pong]("portal1")
      val portal2 = Portal[Ping, Pong]("portal2")

      val repliersrc = Workflows[Nothing, Nothing]("replier")
        .source(Generators.empty.stream)

      val _ = repliersrc
        .portal(portal1)
        .replier[Nothing] { _ => () } { case Ping(x) =>
          testerReplier1.enqueueEvent(x)
          reply(Pong(x - 1))
        }
        .sink()

      val _ = repliersrc
        .portal(portal2)
        .replier[Nothing] { _ => () } { case Ping(x) =>
          testerReplier2.enqueueEvent(x)
          reply(Pong(x - 2))
        }
        .sink()

      def recAwait(x: Int, portal: AtomicPortalRef[Ping, Pong], tester: TestUtils.Tester[Int])(using
          AskerTaskContext[Int, Int, Ping, Pong]
      ): Unit =
        val future: Future[Pong] = portal.ask(Ping(x))
        future.await {
          tester.enqueueEvent(future.value.get.x)
          ctx.emit(future.value.get.x)
          if future.value.get.x > 0 then recAwait(future.value.get.x, portal, tester)
        }

      val asker = Workflows[Int, Int]("asker")
        .source(generator.stream)
        .portal(portal1, portal2)
        .asker[Int] { x =>
          recAwait(x, portal1, testerAsker1)
          recAwait(x, portal2, testerAsker2)
        }
        // .logger()
        .sink()
        .freeze()
    }

    val system = Systems.test()

    system.launch(app)

    system.stepUntilComplete()

    system.shutdown()

    Range(1024, 0, -1).foreach { x =>
      testerReplier1.receiveAssert(x)
      testerAsker1.receiveAssert(x - 1)
    }

    Range(1024, 0, -2).foreach { x =>
      testerReplier2.receiveAssert(x)
      testerAsker2.receiveAssert(x - 2)
    }

    testerReplier1.isEmptyAssert()
    testerReplier2.isEmptyAssert()
    testerAsker1.isEmptyAssert()
    testerAsker2.isEmptyAssert()

  end testMultiplePortals // def
