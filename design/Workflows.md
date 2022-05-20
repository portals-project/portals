```scala
// A workflow is a DAG of tasks with sources and sinks.
// Tasks are connected through channels. 
// The workflow executes atoms atomically, i.e. all events within an atom are executed together to completion, and no other atoms are executed concurrently.

// A workflow can be built from tasks and the channels between the tasks.
val wf = Workflow(tasks = ..., channels = ..., sources = ..., sinks = ...)

// A workflow can be created by using a Workflow factory method.
// Incremental wordcount example.
val wf = Workflows
    .builder().withName("workflow") // give name
    // input is a stream of lines of text, we give it the name "text"
    .source[String]()
    .withName("text")
    // each line is split on whitespace
    .flatMap(_.split("\\s+"))
    // we create tuples for each word, with the second element being the number of occurrences of the word
    .map(word => (word, 1))
    // keyBy groups every tuple by the first element
    // we can implement keyBy by the following map operator. It explicitly sets the key using the context.:
    // .map(ctx ?=> (k, v) => ctx.key = k; (k, v))
    .keyBy(_._1)
    // we compute the incrementally updated wordcount for each word using the 
    // state provided by ctx
    // explicit reduce task
    .map(ctx ?=> (k, v) => 
      ctx.state.get(key)  match
        case Some(count) => 
          ctx.state.set(key, count + v)
          (k, count + v)
        case None =>
          ctx.state.set(key, v)
          (k, count + v)
    )
    .sink[(String, Int)]()
    .withName("counts")
    // calling build assembles the workflow
    .build()

// execute workflow
system.launch(wf)

// send some events to the workflow
val wfconnection = ... // see System
wfconnection.submit("hello world world hello")
wfconnection.fuse()
wfconnection.submit("hallo welt welt hallo")
wfconnection.fuse()
wfconnection.close()


// we can create a cycle in the workflow the following way
val builder = Workflows.builder()

val cycle = builder.cycle() // create cycle source

val src = builder
  .source[Int]

val loop = builder
  .merge(src, cycle)
  .map(_ + 1)
  .intoCycle(cycle)

val wf = builder.build().launch()


// we can create a DAG and not just a sequence the following way
// this creates a diamond shaped workflow
//          |------> map _ + 1 ---->
// source -->                      |---> sink
//          |------> map _ + 2 ---->
val builder = Workflows.builder().withName("wf")

val source = builder
  .source[Int]()

val fromSource1 = builder
  .from(source)
  .map(_ + 1)

val fromSource2 = builder
  .from(source)
  .map(_ + 2)

val merged = builder
  .merge(fromSource1, fromSource2)

val sink = builder
  .from(merged)
  .sink[Int]()
  .withName("sink")
```