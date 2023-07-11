package portals.api.builder

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test

import portals.api.builder.TaskExtensions.*
import portals.api.dsl.DSL
import portals.application.task.PerKeyState
import portals.application.task.PerTaskState
import portals.test.*
import portals.test.TestUtils
import portals.util.Key

@RunWith(classOf[JUnit4])
class FlowBuilderTest:

  @Test
  def testDiamondTaskGraph(): Unit =
    import portals.api.dsl.DSL.*

    val testData = List(List(1))

    val flows = TestUtils.flowBuilder[Int, Int] { flow =>
      val flow1 = flow.map(x => x + 1)
      val flow2 = flow.map(x => x + 2)
      val flow3 = flow.map(x => x + 3)
      val merged = flow1.union(flow2).union(flow3)
      merged
    }

    val tester = TestUtils.executeWorkflow(flows, testData)

    // 1. The output does not contain 1
    assertFalse(tester.contains(1))

    // 2. The output contains 2, 3, 4 from each of the three paths
    assertTrue(tester.contains(2))
    assertTrue(tester.contains(3))
    assertTrue(tester.contains(4))

    // 3. The output contains a single atom
    assertEquals(1, tester.receiveAllWrapped().filter { case TestUtils.Tester.Atom => true; case _ => false }.size)

  @Test
  def testSteppers(): Unit =
    import portals.api.dsl.DSL.*

    val testData = List.fill(10)(0).grouped(1).toList

    val flows = TestUtils.flowBuilder[Int, Int] {
      _.map { x => x + 1 }
        .withStep { TaskBuilder.map { x => x + 2 } }
        .withStep { TaskBuilder.map { x => x + 3 } }
        .withLoop(2) { TaskBuilder.map { x => x + 0 } }
    }

    val tester = TestUtils.executeWorkflow(flows, testData)

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
  def testWrapper(): Unit =
    import portals.api.dsl.DSL.*

    val testData = List(List(1, 2, 3, 4))

    val flows = TestUtils.flowBuilder[Int, Int] {
      _.map { _ + 5 }
        .withWrapper { ctx ?=> wrapped => event =>
          if event < 3 then ctx.emit(0) else wrapped(event)
        }
    }

    val tester = TestUtils.executeWorkflow(flows, testData)

    // 0, 0, 8, 9
    tester
      .receiveAssert(0)
      .receiveAssert(0)
      .receiveAssert(8)
      .receiveAssert(9)

  @Test
  def testSplitAndUnion(): Unit =
    import portals.api.dsl.DSL.*

    val testData = List.range(0, 4).grouped(1).toList

    val map0 = Map(0 -> "zero", 2 -> "two")
    val map1 = Map(1 -> "one", 3 -> "three")

    val flows = TestUtils.flowBuilder[Int, String] { x =>
      val (split0, split1) = x.split(
        x => x % 2 == 0,
        x => x % 2 == 1,
      )
      val msplit0 = split0.map(map0)
      val msplit1 = split1.map(map1)
      msplit0.union(msplit1)
    }

    val tester = TestUtils.executeWorkflow(flows, testData)

    assertEquals(true, tester.contains("zero"))
    assertEquals(true, tester.contains("one"))
    assertEquals(true, tester.contains("two"))
    assertEquals(true, tester.contains("three"))

  @Test
  def testWithAndThen(): Unit =
    import portals.api.dsl.DSL.*

    val testData = List.range(0, 4).grouped(1).toList

    val flows = TestUtils.flowBuilder[Int, Int] {
      _.filter { _ >= 1 }
        .withAndThen(TaskBuilder.map { _ + 1 })
        .withAndThen(TaskBuilder.map { _ + 2 })
        .withAndThen(TaskBuilder.map { _ + 3 })
        .withAndThen(TaskBuilder.filter(_ < 9))
    }

    val tester = TestUtils.executeWorkflow(flows, testData)

    // , 7, 8, _
    tester
      .receiveAssert(7)
      .receiveAssert(8)

  @Test
  def testVSM(): Unit =
    import portals.api.dsl.DSL.*

    val testData = List.range(0, 3).grouped(1).toList
    val testDataKeys = List.fill(3)(0).map(Key(_)).grouped(1).toList

    object VSM:
      def init: VSMTask[Int, Int] = VSMTasks.processor {
        case 0 =>
          ctx.emit(0)
          VSMTasks.same // stay in init
        case _ =>
          ctx.emit(1)
          started // go to started
      }

      def started: VSMTask[Int, Int] = VSMTasks.processor {
        case 1 =>
          ctx.emit(1)
          VSMTasks.same // stay in started
        case _ =>
          ctx.emit(0)
          init // go to init
      }

    val flows = TestUtils.flowBuilder[Int, Int] { _.vsm { VSM.init } }

    val tester = TestUtils.executeWorkflow(flows, testData, testDataKeys)

    // state transitions: init->init, init->started, started->init: 0 1 0
    tester
      .receiveAssert(0)
      .receiveAssert(1)
      .receiveAssert(0)

  @Test
  def testPerKeyState(): Unit =
    import portals.api.dsl.DSL.*

    val testData = List(List(1, 2, 3), List(1, 2, 3))

    val flows = TestUtils.flowBuilder[Int, Int] {
      _.init {
        val perKeyState: PerKeyState[Int] = PerKeyState("pks", 0)
        TaskBuilder.processor { event =>
          // emit state
          ctx.emit(perKeyState.get())
          // set state to the event
          perKeyState.set(event)
        }
      }
    }

    val tester = TestUtils.executeWorkflow(flows, testData)

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
    import portals.api.dsl.DSL.*

    val testData = List(List(1, 2, 3), List(1, 2, 3))

    val flows = TestUtils.flowBuilder[Int, Int] {
      _.init {
        val perTaskState: PerTaskState[Int] = PerTaskState("pts", 0)
        TaskBuilder.processor { event =>
          // emit state
          ctx.emit(perTaskState.get())
          // set state to the event
          perTaskState.set(event)
        }
      }
    }

    val tester = TestUtils.executeWorkflow(flows, testData)

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
    import portals.api.dsl.DSL.*

    val testData = List(List(1, 2, 3))

    val flows = TestUtils.flowBuilder[Int, Int] {
      _.init {
        val y = 1
        TaskBuilder.map { x => x + y }
      }
    }

    val tester = TestUtils.executeWorkflow(flows, testData)

    // 2, 3, 4, the values of the test data incremented by 1
    tester
      .receiveAssert(2)
      .receiveAssert(3)
      .receiveAssert(4)

  @Test
  def testWithOnAtomComplete(): Unit =
    import portals.api.dsl.DSL.*

    val testData = List(List(1, 2, 3, 4), List(1, 2, 3, 4))

    val flows = TestUtils.flowBuilder[Int, Int] {
      _.init {
        val counter = PerTaskState("counter", 0)
        TaskBuilder.processor { event =>
          counter.set(counter.get() + event)
          ctx.emit(event)
        }
      }
        .withOnAtomComplete { ctx ?=>
          ctx.emit(PerTaskState("counter", 0).get())
        }
    }

    val tester = TestUtils.executeWorkflow(flows, testData)

    // 1, 2, 3, 4, 10, atom, 1, 2, 3, 4, 20
    tester
      .receiveAssert(1)
      .receiveAssert(2)
      .receiveAssert(3)
      .receiveAssert(4)
      .receiveAssert(10)
      .receiveAssert(1)
      .receiveAssert(2)
      .receiveAssert(3)
      .receiveAssert(4)
      .receiveAssert(20)
      .isEmpty()

  @Test
  def testAllWithOnAtomComplete(): Unit =
    import portals.api.dsl.DSL.*

    val testData = List(List(1, 2), List(1, 2))

    val flows = TestUtils.flowBuilder[Int, Int] {
      _.map { _ + 1 }
        .map { _ + 2 }
        .map { _ + 3 }
        .allWithOnAtomComplete { ctx ?=>
          ctx.emit(0)
        }
    }

    val tester = TestUtils.executeWorkflow(flows, testData)

    // 7, 8, unordered{0, 3, 5}, atom 7, 8, unordered{0, 3, 5} atom
    assertEquals(Some(7), tester.receive())
    assertEquals(Some(8), tester.receive())
    assertEquals(Set(Some(0), Some(3), Some(5)), Set(tester.receive(), tester.receive(), tester.receive()))
    assertEquals(Some(7), tester.receive())
    assertEquals(Some(8), tester.receive())
    assertEquals(Set(Some(0), Some(3), Some(5)), Set(tester.receive(), tester.receive(), tester.receive()))
    assertFalse(tester.isEmpty())

  @Test
  def testAllWrapper(): Unit =
    import portals.api.dsl.DSL.*

    val testData = List(List(1, 2, 3, 4), List(1, 2, 3, 4))

    val flows = TestUtils.flowBuilder[Int, Int] {
      _.map { _ + 1 }
        .map { _ + 2 }
        .map { _ + 3 }
        .allWithWrapper { ctx ?=> wrapped => event =>
          if event < 4 then ctx.emit(0)
          else
            wrapped(event)
            wrapped(event)
        }
    }

    val tester = TestUtils.executeWorkflow(flows, testData)

    // twice: 0, 0, 0, 10x8 (eigth tens)
    tester
      // atom 1:
      .receiveAssert(0)
      .receiveAssert(0)
      .receiveAssert(0)
      .receiveAssert(10)
      .receiveAssert(10)
      .receiveAssert(10)
      .receiveAssert(10)
      .receiveAssert(10)
      .receiveAssert(10)
      .receiveAssert(10)
      .receiveAssert(10)
      // atom 2:
      .receiveAssert(0)
      .receiveAssert(0)
      .receiveAssert(0)
      .receiveAssert(10)
      .receiveAssert(10)
      .receiveAssert(10)
      .receiveAssert(10)
      .receiveAssert(10)
      .receiveAssert(10)
      .receiveAssert(10)
      .receiveAssert(10)
      .isEmpty()
