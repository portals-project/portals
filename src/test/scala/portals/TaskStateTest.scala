package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore

@RunWith(classOf[JUnit4])
class TaskStateTest:

  @Test
  def testPerKeyState(): Unit =
    import portals.DSL.*

    val tester = TestUtils.Tester[Int]()

    val builder = ApplicationBuilders.application("app")

    val generator = builder.generators.fromListOfLists("generator", List(List(1, 2, 3), List(1, 2, 3)))

    val workflow = builder
      .workflows[Int, Int]("workflow")
      .source(generator.stream)
      .processor[Int] { ctx ?=> event =>
        val perKeyState: PerKeyState[Int] = PerKeyState("pks", 0)
        // emit state
        ctx.emit(perKeyState.get())
        // set state to the event
        perKeyState.set(event)
      }
      .logger()
      .task(tester.task)
      .sink()
      .freeze()

    val app = builder.build()

    val system = Systems.syncLocal()

    system.launch(app)

    system.stepAll()

    system.shutdown()

    // first we receive the three default values, then we receive the three set values
    tester
      .receiveAssert(0)
      .receiveAssert(0)
      .receiveAssert(0)
      .receiveAssert(1)
      .receiveAssert(2)
      .receiveAssert(3)

  @Test
  def testPerTaskState(): Unit =
    import portals.DSL.*

    val tester = TestUtils.Tester[Int]()

    val builder = ApplicationBuilders.application("app")

    val generator = builder.generators.fromListOfLists("generator", List(List(1, 2, 3), List(1, 2, 3)))

    val workflow = builder
      .workflows[Int, Int]("workflow")
      .source(generator.stream)
      .processor[Int] { ctx ?=> event =>
        val perTaskState: PerTaskState[Int] = PerTaskState("pts", 0)
        // emit state
        ctx.emit(perTaskState.get())
        // set state to the event
        perTaskState.set(event)
      }
      // .logger()
      .task(tester.task)
      .sink()
      .freeze()

    val app = builder.build()

    val system = Systems.syncLocal()

    system.launch(app)

    system.stepAll()

    system.shutdown()

    // first we receive the default value, then we receive the set values (delayed by 1 event)
    tester
      .receiveAssert(0)
      .receiveAssert(1)
      .receiveAssert(2)
      .receiveAssert(3)
      .receiveAssert(1)
      .receiveAssert(2)
