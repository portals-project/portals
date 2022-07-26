package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

@RunWith(classOf[JUnit4])
class StepperTest:

  @Test
  def testStepper(): Unit =
    import portals.DSL.*

    val tester = TestUtils.Tester[Int]()

    val builder = ApplicationBuilders.application("app")

    val testData = List.fill(10)(0).grouped(1).toList
    val generator = builder.generators.fromListOfLists("generator", testData)

    val workflow = builder
      .workflows[Int, Int]("workflow")
      .source(generator.stream)
      .task(
        Tasks
          .map[Int, Int] { x => x + 1 }
          .withStep { Tasks.map { x => x + 2 } }
          .withStep { Tasks.map { x => x + 3 } }
          .withLoop(2) { Tasks.map { x => x + 0 } }
      )
      // .logger()
      .task(tester.task)
      .sink()

    val app = builder.build()

    val system = Systems.syncLocal()

    system.launch(app)

    system.stepAll()

    system.shutdown()

    // 1, 2, 3, loop(0, 0), 1, 2, 3, loop(0, 0)
    tester
      .receiveAssert(1)
      .receiveAssert(2)
      .receiveAssert(3)
      .receiveAssert(0)
      .receiveAssert(0)
      .receiveAssert(1)
      .receiveAssert(2)
      .receiveAssert(3)
      .receiveAssert(0)
      .receiveAssert(0)
