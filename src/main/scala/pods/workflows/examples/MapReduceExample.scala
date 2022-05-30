package pods.workflows.examples

/** MapReduce Examples
  *
  * These examples show how we can model the MapReduce paradigm with the Pods
  * Workflows.
  *
  * A MapReduce job executes three steps.
  *   1. Map: The Map step takes the map function and applies it to the input
  *      data. The output data is a collection of key-value pairs.
  *   1. Shuffle: The shuffle step takes the map output and shuffles it
  *      according to the key of the key-value pairs.
  *   1. Reduce: The Reduce step takes the reduce function and applies it to
  *      each group of values that is grouped by key.
  *
  * A MapReduce job is simply modeled as a workflow with three tasks, one for
  * each step (map, shuffle, reduce).
  */

// /** Word Count
//   *
//   * The first example is the canonical word count example. We have an input of
//   * streams of strings, each string is a line of text, the strings are split on
//   * whitespace to form words, and from this we count the number of occurence of
//   * each word.
//   */
// @main def WordCount() =
//   import pods.workflows.*
//   import pods.workflows.DSL.*

//   // our map function takes an string and splits it to produce a list of words
//   val mapper: String => Seq[(String, Int)] =
//     line => line.split("\\s+").map(w => (w, 1))

//   // our reduce function takes two mapped elements and adds the counts together
//   val reducer: ((String, Int), (String, Int)) => (String, Int) = 
//     ((x, y) =>  (x._1, x._2 + y._2))

//   // one naive implementation is to use local state for storing the counts
//   val builder = Workflows
//     .builder()
//     .withName("wf")

//   val flow = builder
//     .source[String]()
//     .withName("input")
//     /* mapper */
//     .flatMap(mapper)
//     .withName("map")
//     // .keyBy(_._1) // sets the contextual key to the word
//     // reducer applied to word and state in the VSM
//     .processor { case (k, v) =>
//       ctx.state.get(k) match
//         case Some(n) =>
//           // reduce to compute the next value
//           val newN = reducer((k, v), (k, n.asInstanceOf))._2
//           ctx.state.set(k, newN)
//         case None => 
//           ctx.state.set(k, v)
//     }
//     // we also install an onAtomComplete handler that is triggered on every atom
//     // it will emit the final state of the VSM
//     .withOnAtomComplete { ctx ?=>
//       // emit final state
//       ctx.state.iterator.foreach { case (k, v) => ctx.emit(k, v) } 
//       ctx.state.clear()
//       ctx.fuse() // emit next atom
//       TaskBehaviors.same
//     }
//     .withLogger() // print the output to logger
//     .sink[(String, Int)]()
//     .withName("output")
  
//   val wf = builder
//     .build()

//   val system = Systems.local()
//   system.launch(wf)

//   val testData = "the quick brown fox jumps over the lazy dog"

//   // to get a reference of the workflow we look in the registry
//   // resolve takes a shared ref and creates an owned ref that points to the shared ref
//   val iref = system.registry[String]("wf/input").resolve(using system) 

//   iref.submit(testData)
//   // iref.submit(FUSE) // fuse the atom to trigger the onAtomComplete handler

//   system.shutdown()


/** Incremental Word Count
  * 
  * The incremental word count example computes the wordcount of a stream of 
  * lines of text. For each new ingested word, the updated wordcount is emitted.
*/
@main def IncrementalWordCount() =
  import pods.workflows.*
  import pods.workflows.DSL.*

  // our map function takes an string and splits it to produce a list of words
  val mapper: String => Seq[(String, Int)] =
    line => line.split("\\s+").map(w => (w, 1))

  // our reduce function takes two mapped elements and adds the counts together
  val reducer: ((String, Int), (String, Int)) => (String, Int) = 
    ((x, y) =>  (x._1, x._2 + y._2))

  // one naive implementation is to use local state for storing the counts
  val builder = Workflows
    .builder()
    .withName("workflow") // give name
  
  val flow = builder
    // input is a stream of lines of text, we give it the name "text"
    .source[String]()
    .withName("text")
    // each line is split on whitespace
    .flatMap(_.split("\\s+"))
    // we create tuples for each word, with the second element being the number of occurrences of the word
    .map(word => (word, 1))
    // keyBy groups every tuple by the first element
    .keyBy(_._1)
    // we compute the incrementally updated wordcount for each word using the 
    // state provided by ctx
    // explicit reduce task
    .map(ctx ?=> (x: (String, Int)) => 
      val (key, value) = (x._1, x._2)
      ctx.state.get(x._1)  match
        case Some(count) => 
          ctx.state.set(key, count.asInstanceOf[Int] + value)
          (key, count.asInstanceOf[Int] + value)
        case None =>
          ctx.state.set(key, value)
          (key, value)
    )
    .withLogger()
    .sink()
    .withName("counts")
    
  val workflow = builder.build()

  val system = Systems.local()
  system.launch(workflow)

  val testData = "the quick brown fox jumps over the lazy dog"

  // to get a reference of the workflow we look in the registry
  // resolve takes a shared ref and creates an owned ref that points to the shared ref
  val iref = system.registry[String]("workflow/text").resolve()

  iref.submit(testData)
  // iref.submit(FUSE)

  system.shutdown()
