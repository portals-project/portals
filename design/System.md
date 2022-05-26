# System

```scala
// to execute anything we need to start an execution environment first
val system = pods.workflows.Systems.local() // start a local runtime
// or connect to some running execution system
val system = pods.workflows.Systems.connect(...)

// then we can launch the workflow
val wf = system.execute(wf) // or, system.launch(wf)

// and terminate a workflow
wf.terminate()

// To send a message to a workflow, we first need to obtain a reference to the workflow.
val wf: RemoteWorkflow = system.registry.workflows("worfklowname")
// We can then inspect the workflows sources and sinks
val iref: RemoteIRef[Int] = wf.sources("sourcename")
// Alternative: this can be discovered directly using the nested namespace
val iref: RemoteIRef[Int] = system.registry.streams("workflowname/sourcename")

// we cannot submit a message to a remote stream directly, we need to obtain a local reference to it first
// resolving a RemoteIRef creates a LocalIRef that is publishes to the RemoteIRef
val localRef: IRef[Int] = ref.resolve()
// or, we can also establish a short-lived connection to the remote iref
val localRef: IRef[Int] = system.streams.shortlivedConnection(ref)

localRef.submit(1)
localRef.fuse()
localRef.complete()
localRef.close()
```