package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

/** ChainOfTasks Test
  *
  * This tests running a chain of tasks
  */
@RunWith(classOf[JUnit4])
class ChainOfTasksTest:

  @Test
  def testChainOfTasks(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilders
      .application("app")

    // 0, Atom, 1, Atom, ..., 4, Atom, Seal
    val input = List.range(0, 5).grouped(1).toList
    val generator = builder.generators.fromListOfLists("hw", input)

    val workflow = builder
      .workflows[Int, Int]("workflow")
      .source[Int](generator.stream)
      // chain length 4
      .map(_ + 1)
      .map(_ + 1)
      .map(_ + 1)
      .map(_ + 1)
      .task(tester.task)
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.syncLocal()
    system.launch(application)

    system.stepAll()
    system.shutdown()

    input.foreach { list =>
      list.foreach { message =>
        // receive message + length of chain (4)
        tester.receiveAssert(message + 4)
      }
    }
