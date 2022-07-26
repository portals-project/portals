package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore

/** ChainOfWorkflows Test
  *
  * This tests running a chain of workflows
  */
@RunWith(classOf[JUnit4])
class ChainOfWorkflowsTest:

  @Ignore
  @Test
  def testChainOfWorkflows(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilders
      .application("app")

    // 0, Atom, 1, Atom, ..., 4, Atom, Seal
    val input = List.range(0, 5).grouped(1).toList
    val generator = builder.generators.fromListOfLists("hw", input)

    def workflowFactory(name: String, stream: AtomicStreamRef[Int]): Workflow[Int, Int] =
      builder
        .workflows[Int, Int](name)
        .source[Int](stream)
        .map(_ + 1)
        .sink()
        .freeze()

    // chain length 4
    val wf1 = workflowFactory("wf1", generator.stream)
    val wf2 = workflowFactory("wf2", wf1.stream)
    val wf3 = workflowFactory("wf3", wf2.stream)
    val wf4 = workflowFactory("wf4", wf3.stream)
    val twf = tester.workflow(wf4.stream, builder)

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
