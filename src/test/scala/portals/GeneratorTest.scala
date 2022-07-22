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
      .fromIterator[Int]("generator", (0 to 10).iterator)

    val workflow = builder
      .workflows[Int, Int]("wf")
      .source(generator.stream)
      .task(tester.task)
      .logger()
      .sink("sink")
      .freeze()

    val app = builder.build()

    val system = Systems.syncLocal()

    // ASTPrinter.println(app)

    system.launch(app)

    system.stepAll()

    system.shutdown()

    (0 to 10).foreach { i =>
      tester.receiveAssert(i)
    }

  @Test
  def fromFunctionTest(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilders
      .application("app")

    val generator = builder.generators
      .generator[Int](
        "generator",
        Generators.fromFunction(
          {
            import Generator.*
            var state: Int = -1
            var atom: Boolean = false
            () =>
              if atom then
                atom = false
                Atom
              else
                state = state + 1
                if state % 5 == 0 then atom = true
                Event(state)
          },
          () => true
        )
      )

    val workflow = builder
      .workflows[Int, Int]("wf")
      .source(generator.stream)
      .task(tester.task)
      .logger()
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
