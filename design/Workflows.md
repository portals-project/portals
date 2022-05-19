```scala
// *** Workflows ***
// A workflow is a DAG of tasks with sources and sinks.
// Tasks are connected through channels. 
// The workflow executes atoms atomically, i.e. all events within an atom are executed together to completion, and no other atoms are executed concurrently.

// A workflow can be built from tasks and the channels between the tasks.
val wf = Workflow(tasks = ..., channels = ..., sources = ..., sinks = ...)

// A workflow can be created by using a Workflow factory method.
// Here we start a worflow builder.
// The workflow builder can be used to create flows (sequences of tasks).
// Wordcount example.
val wf = Workflows
    .builder().withName("workflow") // give name
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
    // we compute the incrementally updated wordcount for each word using the 
    // state provided by ctx
    .map(ctx ?=> (k, v) => 
      ctx.state.get(key)  match
        case Some(count) => 
          ctx.state.set(key, count + v)
          (k, count + v)
        case None =>
          ctx.state.set(key, v)
          (k, count + v)
    )
    .sink[(String, Int)]
    .withName("counts")
    // calling build assembles the workflow
    .build()

// to execute the system we need to start an execution environment, and start the workflow.
val system = Pods.ExecutionSystem()
// or connect to some running execution system
val system = Pods.ExecutionSystem.connect(...)

// and launch the workflow
val ewf = system.execute(wf)
// alternative: 
val ewf = system.launch(wf)

// and close a workflow
ewf.close()

// To send a message to a workflow, we first need to obtain a reference ot the workflow.
val wf: RemoteWorkflow = system.registry.workflows("worfklowname")
// We can then inspect the workflows sources and sinks
val ref: RemoteIRef[Int] = wf.sources("sourcename")
// Alternative: this can be discovered as it is a stream
val ref: RemoteIRef[Int] = system.registry.streams("workflowname/sourcename")

// we cannot submit a message to a remote stream directly, we need to obtain a local reference to it first
// Alternatives:
val localRef: LocalIRef[Int] = ref.resolve()
// or
// establish a short-lived connection to the remote iref
val stream: IRef[Int] = system.streams.ephemeral(ref)
stream.submit(1)
// or
// established a long-lived connection to the remote iref
val stream: IRef[Int] = system.streams.connection(ref)
stream.submit(1)
stream.fuse()
stream.complete()
stream.close()



// TODO: cycles

```
