package portals.api.builder

import scala.annotation.experimental

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test

import portals.api.builder.ApplicationBuilder
import portals.api.dsl.DSL
import portals.system.Systems
import portals.test.*
import portals.test.TestUtils

@RunWith(classOf[JUnit4])
class GeneratorTest:

  @Test
  def fromIteratorTest(): Unit =
    import portals.api.dsl.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilder("app")

    val generator = builder.generators
      .fromIterator[Int](Iterator.range(0, 10))

    val _ = builder
      .workflows[Int, Int]("wf")
      .source(generator.stream)
      .task(tester.task)
      // .logger()
      .sink()
      .freeze()

    val app = builder.build()

    val system = Systems.interpreter()

    // ASTPrinter.println(app)

    system.launch(app)

    system.stepUntilComplete()
    system.shutdown()

    Iterator.range(0, 10).foreach { x =>
      tester.receiveAssert(x)
    }

  @Test
  def fromIteratorOfIteratorsTest(): Unit =
    import portals.api.dsl.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilder("app")

    val generator = builder.generators
      .fromIteratorOfIterators[Int](Iterator.from(0).map { x => Iterator.range(0, 5).map(_ + 5 * x) })

    val workflow = builder
      .workflows[Int, Int]("wf")
      .source(generator.stream)
      .task(tester.task)
      // .logger()
      .sink()
      .freeze()

    val app = builder.build()

    val system = Systems.interpreter()

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

  // @Test
  // @experimental
  // @deprecated
  // def testExternal(): Unit =
  //   import portals.api.dsl.DSL.*

  //   val tester = new TestUtils.Tester[Int]()

  //   val builder = ApplicationBuilder("app")

  //   val (ext_ref, generator) = builder.generators.external[Int]()

  //   val workflow = builder
  //     .workflows[Int, Int]("wf")
  //     .source(generator.stream)
  //     .task(tester.task)
  //     // .logger()
  //     .sink()
  //     .freeze()

  //   val app = builder.build()

  //   val system = Systems.interpreter()

  //   // ASTPrinter.println(app)

  //   system.launch(app)

  //   List.range(0, 10).foreach { i =>
  //     ext_ref.submit(i)
  //     ext_ref.fuse()
  //   }
  //   ext_ref.seal()

  //   system.stepUntilComplete()

  //   system.shutdown()

  //   List.range(0, 10).foreach { i =>
  //     assertEquals(Some(List(i)), tester.receiveAtom())
  //   }
