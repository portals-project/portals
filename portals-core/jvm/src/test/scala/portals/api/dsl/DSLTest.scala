package portals.api.dsl

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test

import portals.api.builder.TaskBuilder
import portals.api.dsl.DSL.*
import portals.system.Systems
import portals.test.TestUtils

@RunWith(classOf[JUnit4])
class DSLTest:

  @Test
  def testContextDSL(): Unit =
    val testData = List.fill(10)(0).grouped(1).toList

    val key = 0

    val task1 = TaskBuilder
      .processor[Int, Int] { x =>
        // context summoned by DSL
        ctx.state.set(key, ctx.state.get(key).asInstanceOf[Option[Int]].getOrElse(0) + 1)
        ctx.emit(ctx.state.get(key).get.asInstanceOf[Int])
      }

    val tester1 = TestUtils.executeTask(task1, testData)

    tester1
      .receiveAssert(1)
      .receiveAssert(2)
      .receiveAssert(3)
      .receiveAssert(4)
      .receiveAssert(5)
      .receiveAssert(6)
      .receiveAssert(7)
      .receiveAssert(8)
      .receiveAssert(9)
      .receiveAssert(10)
      .receiveAtomAssert()
      .receiveSealAssert()

    val task2 = TaskBuilder
      .processor[Int, Int] { x =>
        // use top-level DSL functions that use summoned context
        state.set(key, state.get(key).asInstanceOf[Option[Int]].getOrElse(0) + 1)
        emit(state.get(key).get.asInstanceOf[Int])
      }

    val tester2 = TestUtils.executeTask(task2, testData)

    tester2
      .receiveAssert(1)
      .receiveAssert(2)
      .receiveAssert(3)
      .receiveAssert(4)
      .receiveAssert(5)
      .receiveAssert(6)
      .receiveAssert(7)
      .receiveAssert(8)
      .receiveAssert(9)
      .receiveAssert(10)
      .receiveAtomAssert()
      .receiveSealAssert()

  @Test
  def testPortalDSL(): Unit =
    val tester = new TestUtils.Tester[Int]()

    val app = PortalsApp("myApp") {
      val triggers = Generators.fromList(List(1, 2, 3))
      val portal = Portal[Int, Int]("portal", x => x.hashCode())

      val replier = Workflows[Nothing, Nothing]("replier")
        .source(Generators.fromList(List.empty).stream)
        .replier[Nothing, Int, Int](portal)(_ => ()) { req =>
          reply(req + 1)
        }
        .sink()
        .freeze()

      val requester = Workflows[Int, Int]("requester")
        .source(triggers.stream)
        .asker(portal) { trigger =>
          val future = ask(portal)(trigger)
          await(future) {
            emit(future.value.get)
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

    tester
      .receiveAssert(2)
      .receiveAssert(3)
      .receiveAssert(4)
    // TODO: one of these will break depending on compiler choice
    // .receiveAtomAssert()
    // .receiveSealAssert()

  @Test
  def testBuilderDSL(): Unit =
    val tester = new TestUtils.Tester[Int]()

    val app = PortalsApp("myApp") {
      val generator = Generators.fromRange(0, 10, 1)
      val sequencer = Sequencers.random[Int]()
      val splitter = Splitters.empty[Int](sequencer.stream)
      val split = Splits.split(splitter, x => x % 2 == 0)
      val workflow = Workflows[Int, Int]("wf")
        .source[Int](split)
        .task(tester.task)
        .sink()
        .freeze()
      Connections.connect(generator.stream, sequencer)
    }

    val system = Systems.test()
    system.launch(app)
    system.stepUntilComplete()
    system.shutdown()

    tester
      .receiveAssert(0)
      .receiveAtomAssert()
      .receiveAssert(2)
      .receiveAtomAssert()
      .receiveAssert(4)
      .receiveAtomAssert()
      .receiveAssert(6)
      .receiveAtomAssert()
      .receiveAssert(8)
      .receiveAtomAssert()
