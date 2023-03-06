package portals.application.task

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test

import portals.api.builder.filter
import portals.api.builder.vsm
import portals.api.builder.withAndThen
import portals.api.builder.withLoop
import portals.api.builder.withStep
import portals.api.builder.withWrapper
import portals.api.builder.TaskBuilder
import portals.api.builder.VSMTask
import portals.api.builder.VSMTasks
import portals.api.dsl.DSL
import portals.application.task.PerKeyState
import portals.application.task.PerTaskState
import portals.test.*
import portals.test.TestUtils
import portals.Key

@RunWith(classOf[JUnit4])
class TasksTest:

  @Test
  def testSteppers(): Unit =
    val testData = List.fill(10)(0).grouped(1).toList

    val task = TaskBuilder
      .map[Int, Int] { x => x + 1 }
      .withStep { TaskBuilder.map { x => x + 2 } }
      .withStep { TaskBuilder.map { x => x + 3 } }
      .withLoop(2) { TaskBuilder.map { x => x + 0 } }

    val tester = TestUtils.executeTask(task, testData)

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
    val testData = List(List(1, 2, 3, 4))

    val task = TaskBuilder
      .map[Int, Int] { _ + 5 }
      .withWrapper { ctx ?=> wrapped => event =>
        if event < 3 then ctx.emit(0) else wrapped(event)
      }

    val tester = TestUtils.executeTask(task, testData)

    // 0, 0, 8, 9
    tester
      .receiveAssert(0)
      .receiveAssert(0)
      .receiveAssert(8)
      .receiveAssert(9)

  @Test
  def testWithAndThen(): Unit =
    val testData = List.range(0, 4).grouped(1).toList
    import portals.api.dsl.DSL.*
    val task = TaskBuilder
      .filter[Int](_ >= 1)
      .withAndThen(TaskBuilder.map { _ + 1 })
      .withAndThen(TaskBuilder.map { _ + 2 })
      .withAndThen(TaskBuilder.map { _ + 3 })
      .withAndThen(TaskBuilder.filter(_ < 9))

    val tester = TestUtils.executeTask(task, testData)

    // , 7, 8, _
    tester
      .receiveAssert(7)
      .receiveAssert(8)

  @Test
  def testVSM(): Unit =
    import portals.api.dsl.DSL.*

    val testData = List.range(0, 3).grouped(1).toList
    val testDataKeys = List.fill(3)(0).map(Key[Long](_)).grouped(1).toList

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

    val task = TaskBuilder.vsm { VSM.init }

    val tester = TestUtils.executeTask(task, testData, testDataKeys)

    // state transitions: init->init, init->started, started->init: 0 1 0
    tester
      .receiveAssert(0)
      .receiveAssert(1)
      .receiveAssert(0)

  @Test
  def testPerKeyState(): Unit =
    import portals.api.dsl.DSL.*

    val testData = List(List(1, 2, 3), List(1, 2, 3))

    val task = TaskBuilder.processor[Int, Int] { ctx ?=> event =>
      val perKeyState: PerKeyState[Int] = PerKeyState("pks", 0)
      // emit state
      ctx.emit(perKeyState.get())
      // set state to the event
      perKeyState.set(event)
    }

    val tester = TestUtils.executeTask(task, testData)

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

    val task = TaskBuilder.processor[Int, Int] { ctx ?=> event =>
      val perTaskState: PerTaskState[Int] = PerTaskState("pts", 0)
      // emit state
      ctx.emit(perTaskState.get())
      // set state to the event
      perTaskState.set(event)
    }

    val tester = TestUtils.executeTask(task, testData)

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
    val testData = List(List(1, 2, 3))

    val task = TaskBuilder.init[Int, Int] {
      val y = 1
      TaskBuilder.map { x => x + y }
    }

    val tester = TestUtils.executeTask(task, testData)

    // 2, 3, 4, the values of the test data incremented by 1
    tester
      .receiveAssert(2)
      .receiveAssert(3)
      .receiveAssert(4)
