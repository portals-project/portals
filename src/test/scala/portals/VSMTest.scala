package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

@RunWith(classOf[JUnit4])
class VSMTest:

  @Test
  def testVSM(): Unit =
    import portals.DSL.*

    val tester = TestUtils.Tester[Int]()

    val builder = ApplicationBuilders.application("app")

    val generator = builder.generators.fromList("generator", List(0, 1, 2), List(0, 0, 0).map(Key(_)))

    object StateMachine:
      def init: Task[Int, Int] = Tasks.vsm {
        case 0 =>
          ctx.emit(0)
          Tasks.same // stay in init
        case _ =>
          ctx.emit(1)
          started // go to started
      }

      def started: Task[Int, Int] = Tasks.vsm[Int, Int] {
        case 1 =>
          ctx.emit(1)
          Tasks.same // stay in started
        case _ =>
          ctx.emit(0)
          init // go to init
      }

    val workflow = builder
      .workflows[Int, Int]("workflow")
      .source(generator.stream)
      .task { Tasks.vsminit { StateMachine.init } }
      // .logger()
      .task(tester.task)
      .sink()

    val app = builder.build()

    val system = Systems.syncLocal()

    system.launch(app)

    system.stepAll()

    system.shutdown()

    // state transitions: init->init, init->started, started->init: 0 1 0
    tester
      .receiveAssert(0)
      .receiveAssert(1)
      .receiveAssert(0)
