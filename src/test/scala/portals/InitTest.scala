package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

@RunWith(classOf[JUnit4])
class InitTest:

  @Test
  def testInit(): Unit =
    import portals.DSL.*

    val tester = TestUtils.Tester[Int]()

    val builder = ApplicationBuilders.application("app")

    val generator = builder.generators.fromList("generator", List(1, 2, 3))

    val workflow = builder
      .workflows[Int, Int]("workflow")
      .source(generator.stream)
      .task {
        Tasks.init {
          val y = TaskStates.perTask("y", 1)
          Tasks.map { x => x + y.get() }
        }
      }
      // .logger()
      .task(tester.task)
      .sink()

    val app = builder.build()

    val system = Systems.syncLocal()

    system.launch(app)

    system.stepAll()

    system.shutdown()

    tester
      .receiveAssert(2)
      .receiveAssert(3)
      .receiveAssert(4)
