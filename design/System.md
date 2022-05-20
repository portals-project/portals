# System

```scala
// to execute anything we need to start an execution environment first
val system = Pods.ExecutionSystem()
// or connect to some running execution system
val system = Pods.ExecutionSystem.connect(...)

// then we can launch the workflow
val ewf = system.execute(wf)
// alternative: 
val ewf = system.launch(wf)

// and terminate a workflow
ewf.terminate()

// To send a message to a workflow, we first need to obtain a reference to the workflow.
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
val stream: IRef[Int] = system.streams.shortlivedConnection(ref)
stream.submit(1)
// or
// established a long-lived connection to the remote iref
val stream: IRef[Int] = system.streams.connection(ref)
stream.submit(1)
stream.fuse()
stream.complete()
stream.close()
```