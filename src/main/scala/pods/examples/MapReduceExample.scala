// package pods.examples

// /** MapReduce Examples
//   *
//   * These examples show how we can model the MapReduce paradigm with the Pods
//   * Workflows.
//   *
//   * A MapReduce job executes three steps.
//   *   1. Map: The Map step takes the map function and applies it to the input
//   *      data. The output data is a collection of key-value pairs.
//   *   1. Shuffle: The shuffle step takes the map output and shuffles it
//   *      according to the key of the key-value pairs.
//   *   1. Reduce: The Reduce step takes the reduce function and applies it to
//   *      each group of values that is grouped by key.
//   *
//   * A MapReduce job is simply modeled as a workflow with three tasks, one for
//   * each step (map, shuffle, reduce).
//   */

// /** Word Count
//   *
//   * The first example is the canonical word count example. We have an input of
//   * streams of strings, each string is a line of text, the strings are split on
//   * whitespace to form words, and from this we count the number of occurence of
//   * each word.
//   */
// @main def MapReduceExample() =
//   import pods.workflows.*

//   // our map function takes an string and splits it to produce a list of words
//   val mapper: String => Seq[(String, Int)] =
//     line => line.split("\\s+").map(w => (w, 1))

//   // our reduce function takes two counts and adds them
//   val reducer: (Int, Int) => Int = (_ + _)

//   // one naive implementation is to use local state for storing the counts
//   val wf = Workflows
//     .builder()
//     .source[String]
//     .withName("input")
//     // mapper
//     .flatMap(mapper)
//     .withName("map")
//     // reducer applied to word and state in the VSM
//     .vsm { ctx ?=> (k, v) =>
//       ctx.state.get(k) match {
//         case Some(n) =>
//           ctx.state.put(k, reducer(v, n))
//         case None => ctx.state.put(k, v)
//       }
//     }
//     // we also install a TickHandler that is triggered on every Tick
//     // it will emit the final state of the VSM
//     .withTickHandler { ctx ?=>
//       ctx.state.iterator.foreach { case (k, v) => ctx.emit(k, v) }
//       ctx.state.clear()
//     }
//     .withLogger()
//     .sink[(String, Int)]
//     .withName("output")
//     .build()

//   val system = System()
//   system.execute(wf)

//   val testData = "the quick brown fox jumps over the lazy dog"
//   val _ = system.submit(wf("input"), testData)
//   val _ = system.submit(wf("input"), Tick)

//   system.close()
