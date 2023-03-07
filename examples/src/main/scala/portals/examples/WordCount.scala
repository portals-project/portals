package portals.examples

import portals.api.builder.ApplicationBuilder
import portals.api.builder.TaskBuilder
import portals.api.dsl.DSL
import portals.application.task.PerTaskState
import portals.system.Systems

/** Word Count
  *
  * This example shows how we can implement the Word Count example in the style
  * of MapReduce.
  *
  * A MapReduce job executes three steps.
  *   1. Map: The Map step takes the map function and applies it to the input
  *      data. The output data is a collection of key-value pairs. 2. Shuffle:
  *      The shuffle step takes the map output and shuffles it according to the
  *      key of the key-value pairs. 3. Reduce: The Reduce step takes the reduce
  *      function and applies it to each group of values that is grouped by key.
  *
  * A MapReduce job is simply modeled as a workflow with three tasks, one for
  * each step (map, shuffle, reduce).
  *
  * Word Count. Here we show how to implement the canonical word count
  * example.We have an input of streams of strings, each string is a line of
  * text, the strings are split on whitespace to form words, and from this we
  * count the number of occurence of each word.
  */

@main def WordCount(): Unit =
  import portals.api.dsl.DSL.*

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
    .logger()
    .checkExpectedType[(String, Int)]()
    .sink()
    .freeze()

  val application = builder
    .build()

  val system = Systems.interpreter()
  system.launch(application)

  system.stepUntilComplete()
  system.shutdown()
