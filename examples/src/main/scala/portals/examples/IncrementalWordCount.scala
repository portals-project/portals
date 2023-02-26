package portals.examples

import portals.*

/** Incremental Word Count
  *
  * Streaming Dataflow is straight-forward to implement in our model. We build a streaming pipeline from sources, apply
  * transformations on the data through the processor abstraction, and end in a sink. The processor abstraction can then
  * be used to implement all higher-level operators that are common in streaming dataflow, such as Map, FlatMap,
  * Windowing, etc.
  *
  * The incremental word count example computes the wordcount of a stream of lines of text. For each new ingested word,
  * the updated wordcount is emitted.
  */
@main def IncrementalWordCount(): Unit =
  import portals.DSL.*

  val builder = ApplicationBuilder("application")

  val input = List("the quick brown fox jumps over the lazy dog")
  val generator = builder.generators.fromList(input)

  val _ = builder
    .workflows[String, (String, Int)]("workflow")
    // input is a stream of lines of text, we give it the name "text"
    .source[String](generator.stream)
    // each line is split on whitespaces
    .flatMap { _.split("\\s+").toSeq }
    // we create tuples for each word, with the second element being the number of occurrences of the word
    .map { word => (word, 1) }
    // keyBy groups every tuple by the first element
    .key { _._1.hashCode() }
    // we compute the incrementally updated wordcount for each word using the
    // state provided by ctx
    // explicit reduce task
    .init {
      val count = PerKeyState[Int]("count", 0)
      TaskBuilder.map { case (key, value) =>
        count.set(count.get() + value)
        (key, count.get())
      }
    }
    .logger()
    .sink()
    .freeze()

  val application = builder.build()

  // ASTPrinter.println(application)

  val system = Systems.test()
  system.launch(application)

  system.stepUntilComplete()
  system.shutdown()

@main def IncrementalWordCountWithMapperReducer(): Unit =
  import portals.DSL.*

  // our map function takes an string and splits it to produce a list of words
  val mapper: String => Seq[(String, Int)] =
    line => line.split("\\s+").toSeq.map(w => (w, 1))

  // our reduce function takes two mapped elements and adds the counts together
  val reducer: String => Int => Int => (String, Int) =
    s => x => y => (s, x + y)

  val builder = ApplicationBuilder("application")

  val input = List("the quick brown fox jumps over the lazy dog")
  val generator = builder.generators.fromList(input)

  val _ = builder
    .workflows[String, (String, Int)]("workflow")
    .source[String](generator.stream)
    .flatMap { mapper(_) }
    .key { _._1.hashCode() }
    .init {
      val state = PerKeyState[Int]("state", 0)
      TaskBuilder.map { x =>
        val out = reducer(x._1)(state.get())(x._2); state.set(out._2); out
      }
    }
    .logger()
    .sink()
    .freeze()

  val application = builder.build()

  // ASTPrinter.println(application)

  val system = Systems.test()
  system.launch(application)

  system.stepUntilComplete()
  system.shutdown()
