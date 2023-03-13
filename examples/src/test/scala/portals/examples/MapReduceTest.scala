package portals.examples

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Test

import portals.api.builder.ApplicationBuilder
import portals.api.builder.TaskBuilder
import portals.application.task.PerTaskState
import portals.system.Systems
import portals.test.TestUtils

@RunWith(classOf[JUnit4])
class WordCountTest:

  @Test
  def testWordCount(): Unit =
    import portals.api.dsl.DSL.*

    val tester = new TestUtils.Tester[(String, Int)]()
    // one naive implementation is to use local state for storing the counts
    val builder = ApplicationBuilder("application")

    val input = List("the quick brown fox jumps over the lazy dog")
    val generator = builder.generators.fromList(input)

    val _ = builder
      .workflows[String, (String, Int)]("wf")
      .source[String](generator.stream)
      .flatMap(line => line.split("\\s+").toSeq.map(w => (w, 1)))
      .key(_._1.hashCode()) // sets the contextual key to the word
      // reducer applied to word and state in the VSM
      .init[(String, Int)] {
        val counts = PerTaskState[Map[String, Int]]("counts", Map.empty)
        TaskBuilder.processor { case (k, v) =>
          val newCount = counts.get().getOrElse(k, 0) + v
          counts.set(counts.get() + (k -> newCount))
        }
      }
      // we also install an onAtomComplete handler that is triggered on every atom
      // it will emit the final state of the VSM
      .withOnAtomComplete { ctx ?=>
        // emit final state
        val counts = PerTaskState[Map[String, Int]]("counts", Map.empty)
        counts.get().iterator.foreach { case (k, v) => ctx.emit(k, v) }
        ctx.state.clear()
      }
      // .logger()
      // check that the current output type is (String, Int), otherwise something went wrong
      .checkExpectedType[(String, Int)]()
      .task(tester.task)
      .sink()
      .freeze()

    val application = builder
      .build()

    val system = Systems.interpreter()
    system.launch(application)

    system.stepUntilComplete()
    system.shutdown()

    // check that the output is correct
    assertTrue(tester.contains(("the", 2)))
    assertTrue(tester.contains(("quick", 1)))
    assertTrue(tester.contains(("brown", 1)))
    assertTrue(tester.contains(("fox", 1)))
    assertTrue(tester.contains(("jumps", 1)))
    assertTrue(tester.contains(("over", 1)))
    assertTrue(tester.contains(("lazy", 1)))
    assertTrue(tester.contains(("dog", 1)))
