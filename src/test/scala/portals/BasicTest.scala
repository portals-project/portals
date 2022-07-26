package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

@RunWith(classOf[JUnit4])
class BasicTest:

  @Test
  def testIdentity(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilders.application("application")

    val testData = List.range(0, 128).grouped(1).toList
    val generator = builder.generators.fromListOfLists("generator", testData)

    val source = builder
      .workflows[Int, Int]("wf")
      .source[Int](generator.stream)
      .identity()
      .task(tester.task)
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.syncLocal()
    system.launch(application)

    system.stepAll()
    system.shutdown()

    assertEquals(testData.flatten, tester.receiveAll())
