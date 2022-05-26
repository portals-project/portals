# Workflows

```scala
// A workflow is a DAG of tasks with sources and sinks.
// Tasks are connected through streams. 
// The workflow executes atoms atomically, i.e. all events within an atom are 
// executed together to completion, and no other atoms are executed concurrently.

// A workflow can be built from tasks and the connections between the tasks.
val wf = Workflow(tasks = ..., channels = ..., sources = ..., sinks = ...)

// A workflow can be created by using a Workflow factory method.
// This is an example of a workflow that performs an incremental wordcount.
val wf = Workflows
  .builder()
  .withName("workflow") // give name
  // input is a stream of lines of text, we give it the name "text"
  .source[String]()
  .withName("text")
  // each line is split on whitespace
  .flatMap(_.split("\\s+"))
  // MAP
  // we create tuples for each word, with the second element being the number of occurrences of the word
  .map(word => (word, 1))
  // keyBy groups every tuple by the first element
  // we can implement keyBy by the following map operator. It explicitly sets the key using the context.:
  // .map(ctx ?=> (k, v) => ctx.key = k; (k, v))
  .keyBy(_._1)
  // REDUCE
  // we compute the incrementally updated wordcount for each word using the 
  // state provided by ctx, and explicitly reduce the state.
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
val iref = ... // see System
iref.submit("hello world world hello")
iref.fuse()
iref.submit("hallo welt welt hallo")
iref.fuse()
iref.close()


// we can create a cycle in the workflow the following way
val builder = Workflows.builder()

val cycle = builder.cycle[Int]() // create cycle source

val src = builder
  .source[Int]()

val loop = builder
  .merge(src, cycle)
  .map(_ + 1)
  .intoCycle(cycle)


// we can create a DAG and not just a sequence the following way
// this creates a diamond shaped workflow
//          |------> map _ + 1 ---->
// source -->                      |---> sink
//          |------> map _ + 2 ---->
val builder = Workflows.builder().withName("wf")

val source = builder
  .source[Int]().withName("source")

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