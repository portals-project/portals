package portals.examples

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Test

import portals.*
import portals.test.*

/** Streaming Dataflow tests
  *
  * These tests show how we can model Streaming Dataflow in our model.
  *
  * Streaming Dataflow is straight-forward to implement in our model, Pods Workflows are a hybrid of dataflow and
  * actors. We build a streaming pipeline from sources, apply transformations on the data through the processor
  * abstraction, and end in a sink. The processor abstraction can then be used to implement all higher-level operators
  * that are common in streaming dataflow, such as Map, FlatMap, Windowing, etc.
  */

/** Incremental Word Count
  *
  * The incremental word count test computes the wordcount of a stream of lines of text. For each new ingested word, the
  * updated wordcount is emitted.
  */
@RunWith(classOf[JUnit4])
class IncrementalWordCountTest:

  @Test
  def testIncrementalWordCount(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[(String, Int)]()

    // our map function takes an string and splits it to produce a list of words
    val mapper: String => Seq[(String, Int)] =
      line => line.split("\\s+").map(w => (w, 1))

    // our reduce function takes two mapped elements and adds the counts together
    val reducer: ((String, Int), (String, Int)) => (String, Int) =
      ((x, y) => (x._1, x._2 + y._2))

    // one naive implementation is to use local state for storing the counts
    val builder = ApplicationBuilders.application("application")

    val input = List("the quick brown fox jumps over the lazy dog")
    val generator = builder.generators.fromList(input)

    val _ = builder
      .workflows[String, (String, Int)]("workflow")
      // input is a stream of lines of text, we give it the name "text"
      .source[String](generator.stream)
      // each line is splitted on whitespaces
      .flatMap { _.split("\\s+") }
      // we create tuples for each word, with the second element being the number of occurrences of the word
      .map { word => (word, 1) }
      // keyBy groups every tuple by the first element
      .key { _._1.hashCode() }
      // we compute the incrementally updated wordcount for each word using the
      // state provided by ctx
      // explicit reduce task
      .init {
        val count = PerKeyState[Int]("count", 0)
        Tasks.map { case (key, value) =>
          count.set(count.get() + value)
          (key, count.get())
        }
      }
      // .logger()
      .task(tester.task)
      .sink()
      .freeze()

    val application = builder.build()

    // ASTPrinter.println(application)

    val system = Systems.test()
    system.launch(application)

    system.stepUntilComplete()
    system.shutdown()

    // check that the output is correct
    assertTrue(tester.contains(("the", 1)))
    assertTrue(tester.contains(("the", 2)))
    assertTrue(tester.contains(("quick", 1)))
    assertTrue(tester.contains(("brown", 1)))
    assertTrue(tester.contains(("fox", 1)))
    assertTrue(tester.contains(("jumps", 1)))
    assertTrue(tester.contains(("over", 1)))
    assertTrue(tester.contains(("lazy", 1)))
    assertTrue(tester.contains(("dog", 1)))
