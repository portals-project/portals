package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

@RunWith(classOf[JUnit4])
class WithAndThenTest:

  @Test
  def testWithAndThen(): Unit =
    import portals.DSL.*

    val tester = TestUtils.Tester[Int]()

    val builder = ApplicationBuilders.application("app")

    val testData = List.range(0, 4).grouped(1).toList
    val generator = builder.generators.fromListOfLists("generator", testData)

    val workflow = builder
      .workflows[Int, Int]("workflow")
      .source(generator.stream)
      .task(
        Tasks
          .filter[Int](_ >= 1)
          .withAndThen(Tasks.map { _ + 1 })
          .withAndThen(Tasks.map { _ + 2 })
          .withAndThen(Tasks.map { _ + 3 })
          .withAndThen(Tasks.filter(_ < 9))
      )
      // .logger()
      .task(tester.task)
      .sink()

    val app = builder.build()

    val system = Systems.syncLocal()

    system.launch(app)

    system.stepAll()

    system.shutdown()

    // , 7, 8, _
    tester
      .receiveAssert(7)
      .receiveAssert(8)
