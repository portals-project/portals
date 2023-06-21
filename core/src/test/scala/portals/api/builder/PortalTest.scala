package portals.api.builder

import scala.annotation.experimental

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test

import portals.api.dsl.DSL.ask
import portals.api.dsl.DSL.await
import portals.api.dsl.DSL.ctx
import portals.api.dsl.DSL.reply
import portals.api.dsl.DSL.Generators
import portals.api.dsl.DSL.Portal
import portals.api.dsl.DSL.PortalsApp
import portals.api.dsl.DSL.Workflows
import portals.application.task.AskerTaskContext
import portals.application.task.PerTaskState
import portals.application.Application
import portals.application.AtomicPortalRef
import portals.system.Systems
import portals.test.*
import portals.test.TestUtils
import portals.util.Future

@RunWith(classOf[JUnit4])
class PortalTest:

  @Test
  @experimental
  def testPingPong(): Unit =
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
    import portals.api.dsl.DSL.*
    import portals.api.dsl.ExperimentalDSL.*

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
        // .portal(portal1)
        .replier[Nothing](portal1) { _ => () } { case Ping(x) =>
          testerReplier1.enqueueEvent(x)
          reply(Pong(x - 1))
        }
        .sink()

      val _ = repliersrc
        .replier[Nothing](portal2) { _ => () } { case Ping(x) =>
          testerReplier2.enqueueEvent(x)
          reply(Pong(x - 2))
        }
        .sink()

      def recAwait(x: Int, portal: AtomicPortalRef[Ping, Pong], tester: TestUtils.Tester[Int])(using
          AskerTaskContext[Int, Int, Ping, Pong]
      ): Unit =
        val future: Future[Pong] = ask(portal)(Ping(x))
        future.await {
          tester.enqueueEvent(future.value.get.x)
          ctx.emit(future.value.get.x)
          if future.value.get.x > 0 then recAwait(future.value.get.x, portal, tester)
        }

      val asker = Workflows[Int, Int]("asker")
        .source(generator.stream)
        .asker[Int](portal1, portal2) { x =>
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

  @Test
  @experimental
  def testNestedAsks(): Unit =
    import portals.api.dsl.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val app = PortalsApp("testNestedAsks") {
      val generator = Generators.signal[Int](128)

      val portal = Portal[Int, Int]("portal")

      val wf = Workflows[Int, Int]("wf")
        .source(generator.stream)
        .task(TaskBuilder.askerreplier[Int, Int, Int, Int](portal)(portal) { x =>
          // triggered by signal
          val f: Future[Int] = ask(portal)(x - 1)
          await(f) {
            emit(x)
          }
        } { x =>
          // requests, triggered by ask
          if x > 0 then
            val f: Future[Int] = ask(portal)(x - 1)
            await(f) {
              emit(x)
              reply(x)
            }
          else
            emit(x)
            reply(x)
        })
        .task(tester.task)
        .sink()
        .freeze()
    }

    val system = Systems.test()

    system.launch(app)

    system.stepUntilComplete()

    system.shutdown()

    List.range(0, 128 + 1).foreach { x =>
      tester.receiveAssert(x)
    }

  @Test
  @experimental
  def testNestedAskPingPong(): Unit =
    import portals.api.dsl.DSL.*
    import portals.api.dsl.ExperimentalDSL.*

    val testerPing = new TestUtils.Tester[Int]()

    val testerPong = new TestUtils.Tester[Int]()

    val testerTrigger = new TestUtils.Tester[Int]()

    val app = PortalsApp("NestedAskPingPong") {

      val trigger = Generators.signal[Int](128)

      val ping = Portal[Int, Int]("ping")

      val pong = Portal[Int, Int]("pong")

      def pingerPongerBehavior(pinger: AtomicPortalRef[Int, Int], ponger: AtomicPortalRef[Int, Int]) =
        TaskBuilder.askerreplier[Nothing, Int, Int, Int](pinger)(ponger)(_ => ()) { x =>
          // send request `x-1` to other
          if x > 0 then
            val f: Future[Int] = ask(pinger)(x - 1)
            await(f) {
              emit(x)
              reply(x)
            }
          // else done
          else
            emit(x)
            reply(x)
        }

      val pingWf = Workflows[Nothing, Nothing]("pingWf")
        .source(Generators.empty.stream)
        .task(pingerPongerBehavior(pong, ping))
        .task(testerPing.task)
        .empty[Nothing]()
        .sink()
        .freeze()

      val pongWf = Workflows[Nothing, Nothing]("pongWf")
        .source(Generators.empty.stream)
        .task(pingerPongerBehavior(ping, pong))
        .task(testerPong.task)
        .empty[Nothing]()
        .sink()
        .freeze()

      val triggerWf = Workflows[Int, Int]("triggerWf")
        .source(trigger.stream)
        .task(TaskBuilder.asker[Int, Int, Int, Int](ping, pong) { x =>
          val f: Future[Int] = ask(ping)(x)
          await(f) {
            emit(x)
          }
        })
        .task(testerTrigger.task)
        .sink()
        .freeze()
    }

    val system = Systems.test()

    system.launch(app)

    system.stepUntilComplete()

    system.shutdown()

    List.range(0, 128 + 1).filter(_ % 2 == 0).foreach { x =>
      testerPing.receiveAssert(x)
    }

    List.range(0, 128 + 1).filter(_ % 2 == 1).foreach { x =>
      testerPong.receiveAssert(x)
    }

    testerTrigger.receiveAssert(128)

  @Test
  @experimental
  def testMultipleAsks(): Unit =

    val testerTrigger = new TestUtils.Tester[Int]()

    case class FibQuery(n: Int)

    case class FibResp(resp: Int)

    import portals.api.dsl.DSL.*
    import portals.api.dsl.ExperimentalDSL.*

    val app: Application = PortalsApp("Fibonacci") {
      val portal = Portal[FibQuery, FibResp]("portal")

      val fibWorkflow = Workflows[Nothing, Nothing]("fibWf")
        .source(Generators.empty.stream)
        .askerreplier[Nothing, FibQuery, FibResp](portal)(portal)(_ => ()) { case FibQuery(n) =>
          if n == 0 then reply(FibResp(0))
          else if n == 1 || n == 2 then reply(FibResp(1))
          else
            val future1 = ask(portal)(FibQuery(n - 1))
            val future2 = ask(portal)(FibQuery(n - 2))
            awaitAll(future1, future2) {
              reply(FibResp(future1.value.get.resp + future2.value.get.resp))
            }
        }
        .sink()
        .freeze()

      val queryGenerator = Generators.fromRange(0, 10, 1)
      val queryWorkflow = Workflows[Int, Int]("fibQueryWf")
        .source(queryGenerator.stream)
        .map(x => FibQuery(x))
        .asker[Int](portal) { query =>
          val future = ask(portal)(query)
          await(future) {
            ctx.emit(future.value.get.resp)
          }
        }
        .task(testerTrigger.task)
        .sink()
        .freeze()
    }

    val system = Systems.test()
    system.launch(app)
    system.stepUntilComplete()
    system.shutdown()

    testerTrigger.receiveAssert(0)
    testerTrigger.receiveAssert(1)
    testerTrigger.receiveAssert(1)
    testerTrigger.receiveAssert(2)
    testerTrigger.receiveAssert(3)
    testerTrigger.receiveAssert(5)
    testerTrigger.receiveAssert(8)
    testerTrigger.receiveAssert(13)
    testerTrigger.receiveAssert(21)
    testerTrigger.receiveAssert(34)

  @Test
  @experimental
  def testMultipleAsksToDifferentWorkflows(): Unit =
    import portals.api.dsl.DSL.*
    import portals.api.dsl.ExperimentalDSL.*

    val tester = new TestUtils.Tester[Int]()

    val app = PortalsApp("app") {
      val portal1 = Portal[Int, Int]("portal1")
      val portal2 = Portal[Int, Int]("portal2")

      def genWf = (name: String, portal: AtomicPortalRef[Int, Int], multiplier: Int) => {
        Workflows[Int, Nothing](name)
          .source(Generators.fromRange(0, 10, 1).stream)
          .replier[Nothing](portal) { _ => () } { x =>
            reply(x * multiplier)
          }
          .sink()
          .freeze()
      }
      genWf("wf1", portal1, 1)
      genWf("wf2", portal2, 2)

      val askerWF = Workflows[Int, Int]("askerWF")
        .source(Generators.fromRange(0, 10, 1).stream)
        .asker[Int](portal1, portal2) { ctx ?=> x =>
          val future1 = ask(portal1)(x)
          val future2 = ask(portal2)(x)
          awaitAll(future1, future2) {
            ctx.emit(future1.value.get + future2.value.get)
          }
        }
        .task(tester.task)
        .sink()
        .freeze()
    }

    val system = Systems.test()
    system.launch(app)
    system.stepUntilComplete()
    system.shutdown()

    List.range(0, 30, 3).foreach { x =>
      tester.receiveAssert(x)
    }
