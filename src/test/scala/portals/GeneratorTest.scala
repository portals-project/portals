package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore

import TestUtils.*

@RunWith(classOf[JUnit4])
class GeneratorTest:

  @Test
  def fromIteratorTest(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilders
      .application("app")

    val generator = builder.generators
      .fromIterator[Int]("generator", Iterator.range(0, 10))

    val _ = builder
      .workflows[Int, Int]("wf")
      .source(generator.stream)
      .task(tester.task)
      // .logger()
      .sink("sink")
      .freeze()

    val app = builder.build()

    val system = Systems.syncLocal()

    // ASTPrinter.println(app)

    system.launch(app)

    system.stepAll()
    system.shutdown()

    Iterator.range(0, 10).foreach { x =>
      tester.receiveAssert(x)
    }

  @Test
  def fromIteratorOfIteratorsTest(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilders
      .application("app")

    val generator = builder.generators
      .fromIteratorOfIterators[Int]("generator", Iterator.from(0).map { x => Iterator.range(0, 5).map(_ + 5 * x) })

    val workflow = builder
      .workflows[Int, Int]("wf")
      .source(generator.stream)
      .task(tester.task)
      // .logger()
      .sink("sink")
      .freeze()

    val app = builder.build()

    val system = Systems.syncLocal()

    // ASTPrinter.println(app)

    system.launch(app)

    // take two steps

    system.step()
    system.step()
    (0 until 5).foreach { i =>
      tester.receiveAssert(i)
    }

    // take two more steps
    system.step()
    system.step()
    (5 until 10).foreach { i =>
      tester.receiveAssert(i)
    }
    system.shutdown()
