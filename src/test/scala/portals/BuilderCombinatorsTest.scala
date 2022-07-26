package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

import TestUtils.Tester

@RunWith(classOf[JUnit4])
class BuilderCombinatorsTest:

  private def execute[T, U](
      testData: List[List[T]],
      flows: FlowBuilder[T, U] => FlowBuilder[U, U],
  ): (Tester[U]) =
    val tester = TestUtils.Tester[U]()
    val builder = ApplicationBuilders.application("app")
    val generator = builder.generators.fromListOfLists("generator", testData)
    val workflow = builder
      .workflows[T, U]("workflow")
    flows(
      workflow
        .source(generator.stream)
    )
      .task(tester.task)
      .sink()
      .freeze()
    val app = builder.build()
    val system = Systems.syncLocal()
    system.launch(app)
    system.stepAll()
    system.shutdown()
    tester

  private def execute[T, U](
      testData: List[List[T]],
      testDataKeys: List[List[Key[Int]]],
      flows: FlowBuilder[T, U] => FlowBuilder[U, U],
  ): (Tester[U]) =
    val tester = TestUtils.Tester[U]()
    val builder = ApplicationBuilders.application("app")
    val generator = builder.generators.fromListOfLists("generator", testData, testDataKeys)
    val workflow = builder
      .workflows[T, U]("workflow")
    flows(
      workflow
        .source(generator.stream)
    )
      .task(tester.task)
      .sink()
      .freeze()
    val app = builder.build()
    val system = Systems.syncLocal()
    system.launch(app)
    system.stepAll()
    system.shutdown()
    tester

  private def flowBuilder[T, U](flows: FlowBuilder[T, U] => FlowBuilder[T, U]): FlowBuilder[T, U] => FlowBuilder[T, U] =
    flows

  @Test
  def testBuilderSteppers(): Unit =
    import portals.DSL.*

    val testData = List.fill(10)(0).grouped(1).toList

    val flows = flowBuilder[Int, Int] { flow =>
      flow
        .map { x => x + 1 }
        .withStep { Tasks.map { x => x + 2 } }
        .withStep { Tasks.map { x => x + 3 } }
        .withLoop(2) { Tasks.map { x => x + 0 } }
    }

    val tester = execute(testData, flows)

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

  @Test
  def testBuilderWrapper(): Unit =
    import portals.DSL.*

    val testData = List(List(1, 2, 3, 4))

    val flows = flowBuilder[Int, Int] { flow =>
      flow
        .map { _ + 5 }
        .withWrapper { ctx ?=> wrapped => event =>
          if event < 3 then ctx.emit(0) else wrapped(event)
        }
    }

    val tester = execute(testData, flows)

    // 0, 0, 8, 9
    tester
      .receiveAssert(0)
      .receiveAssert(0)
      .receiveAssert(8)
      .receiveAssert(9)

  @Test
  def testWithAndThen(): Unit =
    import portals.DSL.*

    val testData = List.range(0, 4).grouped(1).toList

    val flows = flowBuilder[Int, Int] { flow =>
      flow
        .filter { _ >= 1 }
        .withAndThen(Tasks.map { _ + 1 })
        .withAndThen(Tasks.map { _ + 2 })
        .withAndThen(Tasks.map { _ + 3 })
        .withAndThen(Tasks.filter(_ < 9))
    }

    val tester = execute(testData, flows)

    // , 7, 8, _
    tester
      .receiveAssert(7)
      .receiveAssert(8)

  @Test
  def testVSM(): Unit =
    import portals.DSL.*

    val testData = List.range(0, 3).grouped(1).toList
    val testDataKeys = List.fill(3)(0).map(Key(_)).grouped(1).toList

    object VSM:
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

    val flows = flowBuilder[Int, Int] { flow =>
      flow
        .vsm { VSM.init }
    }

    val tester = execute(testData, testDataKeys, flows)

    // state transitions: init->init, init->started, started->init: 0 1 0
    tester
      .receiveAssert(0)
      .receiveAssert(1)
      .receiveAssert(0)

  @Test
  def testPerKeyState(): Unit =
    import portals.DSL.*

    val testData = List(List(1, 2, 3), List(1, 2, 3))

    val flows = flowBuilder[Int, Int] { flow =>
      flow
        .init {
          val perKeyState: PerKeyState[Int] = PerKeyState("pks", 0)
          Tasks.processor { event =>
            // set key here, (for now we don't have per-key streams, so this is a hack XP)
            ctx.key = Key(event)
            ctx.state.key = Key(event)
            // emit state
            ctx.emit(perKeyState.get())
            // set state to the event
            perKeyState.set(event)
          }
        }

    }

    val tester = execute(testData, flows)

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

    val testData = List(List(1, 2, 3), List(1, 2, 3))

    val flows = flowBuilder[Int, Int] { flow =>
      flow
        .init {
          val perTaskState: PerTaskState[Int] = PerTaskState("pts", 0)
          Tasks.processor { event =>
            // set key here, (for now we don't have per-key streams, so this is a hack XP)
            ctx.key = Key(event)
            ctx.state.key = Key(event)
            // emit state
            ctx.emit(perTaskState.get())
            // set state to the event
            perTaskState.set(event)
          }
        }

    }

    val tester = execute(testData, flows)

    // first we receive the default value, then we receive the set values (delayed by 1 event)
    tester
      .receiveAssert(0)
      .receiveAssert(1)
      .receiveAssert(2)
      .receiveAssert(3)
      .receiveAssert(1)
      .receiveAssert(2)

  @Test
  def testInit(): Unit =
    import portals.DSL.*

    val testData = List(List(1, 2, 3))

    val flows = flowBuilder[Int, Int] { flow =>
      flow
        .init {
          val y = 1
          Tasks.map { x => x + y }
        }
    }

    val tester = execute(testData, flows)

    // 2, 3, 4, the values of the test data incremented by 1
    tester
      .receiveAssert(2)
      .receiveAssert(3)
      .receiveAssert(4)
