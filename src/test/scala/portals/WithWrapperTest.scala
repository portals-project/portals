package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

@RunWith(classOf[JUnit4])
class WithWrapperTest:

  @Test
  def testWrapper(): Unit =
    import portals.DSL.*

    val tester = TestUtils.Tester[Int]()

    val builder = ApplicationBuilders.application("app")

    val generator = builder.generators.fromList("generator", List(1, 2, 3, 4))

    val workflow = builder
      .workflows[Int, Int]("workflow")
      .source(generator.stream)
      .task {
        Tasks
          .map[Int, Int] { _ + 5 }
          .withWrapper { ctx ?=> wrapped => event =>
            if event < 3 then ctx.emit(0) else wrapped(event)
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
      .receiveAssert(0)
      .receiveAssert(0)
      .receiveAssert(8)
      .receiveAssert(9)
