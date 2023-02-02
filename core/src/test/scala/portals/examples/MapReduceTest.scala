package portals.examples

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Test

import portals.*
import portals.test.*

/** MapReduce tests
  *
  * These tests show how we can model the MapReduce paradigm with the Pods Workflows.
  *
  * A MapReduce job executes three steps.
  *   1. Map: The Map step takes the map function and applies it to the input data. The output data is a collection of
  *      key-value pairs. 2. Shuffle: The shuffle step takes the map output and shuffles it according to the key of the
  *      key-value pairs. 3. Reduce: The Reduce step takes the reduce function and applies it to each group of values
  *      that is grouped by key.
  *
  * A MapReduce job is simply modeled as a workflow with three tasks, one for each step (map, shuffle, reduce).
  */

/** Word Count
  *
  * The first test is the canonical word count test. We have an input of streams of strings, each string is a line of
  * text, the strings are split on whitespace to form words, and from this we count the number of occurence of each
  * word.
  */
@RunWith(classOf[JUnit4])
class WordCountTest:

  @Test
  def testWordCount(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[(String, Int)]()
    // one naive implementation is to use local state for storing the counts
    val builder = ApplicationBuilders.application("application")

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
        Tasks.processor { case (k, v) =>
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

    val system = Systems.test()
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
