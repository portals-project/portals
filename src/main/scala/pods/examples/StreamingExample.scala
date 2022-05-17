package pods.examples

/** Streaming Examples
  *
  * These examples show how we can compose streaming programs with Pods
  * Workflows similar to existing streaming systems like Flink.
  * 
  * Essentially, we can encode the concepts from streaming to our workflows, 
  * in the obvious, straightforward way. We can use the stateful flatMap operator
  * together with the contextual functionality that lets us access and modify 
  * the contextual state, for example the key of the current element.
  */

/** WordCount
  *
  * The first example is the canonical word count example. We have an input
  * stream of strings, each string is a line of text, the strings are split on
  * whitespace to form words, and from these words we count the number of
  * occurrences of each word.
  * 
  * Example adapted from:
  * https://github.com/apache/flink/blob/master/flink-examples/flink-examples-streaming/src/main/scala/org/apache/flink/streaming/scala/examples/wordcount/WordCount.scala
  */
@main def StreamingExample() =
  import pods.workflows.*

  //  Pods execution environment
  val system = Systems.localSystem()

  val wf = Workflows
    .builder()
    // input is a stream of lines of text, we give it the name "text"
    .source[String]
    .withName("text")
    // each line is split on whitespace
    .flatMap(_.split("\\s+"))
    // we create tuples for each word, with the second element being the number of occurrences of the word
    .map(word => (word, 1))
    // keyBy groups every tuple by the first element
    // we can implement keyBy by the following map operator. It explicitly sets the key using the context.:
    // .map(ctx ?=> (k, v) => ctx.key = k; (k, v))
    .keyBy(_._1)
    .map(ctx ?=> (k, v) => 
      ctx.state.get(key)  match
        case Some(count) => 
          ctx.state.set(key, count + v
          ctx.emit(k, count + v)
        case None =>
          ctx.state.set(key, v)
    )
    .sink[(String, Int)]
    .withName("counts")
    // calling build assembles the workflow
    .build()
    // calling execute executes the workflow on the system
    // TODO: this feels weird to have execute here, consider running execute in 
    // a different way.
    .execute(system)

  // add a logger that listens to the "counts" sink
  // TODO: It feels bulky to add a Logger as a workflow this way, perhaps
  // there are better alternatives?
  val logWf = Workflows
    .builder()
    // fromSink creates a source and subscribes this source to the provided sink
    // the lifetime of the connection is then bound to the workflow
    .fromSink[(String, Int)]( wf.sink("counts")) )
    .withName("counts")
    .withLogger()
    .build()
    .execute(system)
  
  // ingest data into the system
  val inWf = Workflows
    .builder()
    .fromElements(List("the quick brown fox", "jumps over the lazy dog"))
    .toSource( wf.source("text") )
    .build()
    .execute(system)

  // closing the system, blocking until there are no more pending events
  system.close()
