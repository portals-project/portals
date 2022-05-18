# Workflows Code Examples

```scala
// **** Streams ****

// This is an overview of the streams API.
// We use the following "tags" to describe the streams API:
// - "core": refering to as being part of the core model.
// - "lib": refering to as being part of the library (not core).
// - "alternative": alternative syntax we may consider.
// - "question": something which is open for discussion.

// Consider some event type
type Event

// A stream can be created by calling a factory method in the Streams object
val stream: Stream[Event] = Streams.stream[Event]()

// Streams have an input reference IRef and an output reference ORef
val iref: StreamIRef[Event] = stream.iref
val oref: StreamORef[Event] = stream.oref

// Note: the IRef and ORef extend the reactive streams Publisher and Subscriber interfaces
// (or at least is very similar).
// The StreamIRef implementation simply forwards any of the events it receives to the ORef.
trait StreamIRef extends java.util.concurrent.Flow.Subscriber:
  ??? 
trait StreamORef extends java.util.concurrent.Flow.Publisher:
  ??? 
trait java.util.concurrent.Flow.Subscriber:
  def onSubscribe(s: Subscription): Unit
  def onNext(t: I): Unit
  def onError(t: Throwable): Unit
  def onComplete(): Unit
trait java.util.concurrent.Flow.Publisher:
  def subscribe(s: Subscriber[_ >: O]): Unit

// We can send events to streams by submitting them to the iref
iref.submit(event)
// Lib: we also support the bang syntax
iref ! event

// Streams are sharded per-key, all events on the stream are associated with some
// key. 
// The key is typically inferred from the context.key
iref.submit(event)(using context)
// Lib:: it can also be specified explicitly, e.g. when submitting events to a stream.
iref.submit(event, key)
// Lib:: when nothing is supplied we could default to some strategy
iref.submit(event)

// Streams can be connected to other streams.
// This can be done by calling the connect method on the Streams object.
// A connection is simply a stream that connects the oref of one stream to the 
// iref of another, which forms a subscription between the oref and iref.
// Once a connection has been established, events that are published by the oref
// will be received by the iref.
// Note: Only complete atoms will be received, the iref will not receive quarks 
// or other half-atoms.
val connection: Connection = Streams.connect(oref, iref)
// A connection can be cancelled (this cancels the subscription)
connection.cancel()

// Lib: We can create streams from existing streams.
// One such way is to combine two or more streams into a single stream.
// Note: this is not necessarily part of the core module, this could be implemented
// as part of a library instead using workflows and streams.
val ostream = Streams.combine[](List(istream1, istream2, ...))
// Lib: combiner strategies.
// We can specify the way in which streams (and the atoms) can be combined 
// through a combiner strategy.
// For example, we could specify that the resulting stream should consist of
// molecules with one atom from each stream, i.e. one atom from each stream is 
// taken to form a new larger atom.
// We can also consider other arbitrary strategies.
val ostream = Streams.combine(strategy)(List(istream1, istream2, ...))

// We can seal a stream, which signals that no more events will be submitted
istream.seal()
// Alternative:
istream.close()

// **** Tick ****
// We can then create a tick on the stream.
// The tick creates an atom of events, that consists of all events that were
// submitted before the tick to the stream, and after the previous tick to the 
// stream.
istream.tick()
// Alternative: this can also be called fuse, to stick with the whole "atom" thing
istream.fuse() 

// *** Commit ***
// A stream should be transactional, that is we should be able to perform a 
// 2-phase-commit to ensure that the data is persisted.

// Precommit, returns commit id which can be used to track if commit was executed,
// this is needed in case the client fails, and wants to know later on if the
// commit was executed or not.
val commit_id = istream.precommit()

// block until tick is atomically committed
istream.commit()
// or commit async
istream.commitAsync()

// check if the commit was executed
istream.isCommitted(commit_id)

// if something went wrong we can also rollback (before the commit has completed)
istream.rollback()

// *** Replay ***
// A stream should be able to events starting from some sequence number.
// This can be used to recover from failures.
// This is achieved by calling the replayFrom method on the subscription.
subscription.replayFrom(sequence_number)
```

```scala
// *** Tasks ***

// A task implements the following trait.
trait Task[I, O] extends Flow.Subscriber:
  // The context is swapped during runtime
  private[pods] var ctx: Context = null
  override def onSubscribe(s: Subscription): Unit
  override def onNext(t: I): Unit
  override def onError(t: Throwable): Unit
  override def onComplete(): Unit
  def onTick(): Unit
  // Alterantive: call this onAtomComplete
  def onAtomComplete(): Unit

// lib: We can create a task using a Task factory method.
// The vsm factory is the most powerful factory, it creates a per-key 
// virtual-state-machine task.
val task: Task = Tasks.vsm { ctx ?=> event =>
  // do something with the event
  // ...
  // choose behavior for next receive
  Tasks.same // compare with Akka Typed
}
// further extension methods allow us to modify the members of the task
.withOnSubscribe( ctx ?=> subscription => ... )
.withOnNext(...)
.withOnError(...)
.withOnComplete(...)
.withOnTick(...)

// We should note here that in Scala 3 it is neat that the contextual parameter
// (contextual params use ?=>) can be omitted like the following, this will
// just the way we expect it to:
val task = Tasks.vsm { event => 
  // handle event
  // ...
  // we can still access the context from the implicit scope
  summon[Context].log.info("hello world") 
  // although it probably is nicer to expose this as a method in a DSL instead
  import pods.DSL.*
  ctx.log.info("hello world") // ctx is a method call that summons the Context
  Tasks.same // don't forget to return a behavior :)
}

// There are many more factory methods available, as most situations don't require
// a virtual state machine abstraction.
// The processor factory gets the ctx object together with the event, but does
// not require us to return the behavior for the next receive.
val identity = Tasks.processor { ctx ?=> event =>
  ctx.emit(event)
}
// Similarly, we have map, flatMap, and more as you would expect. These can easily
// be built from stronger primitives such as the processor.

// CONTINUE HERE:

// TODO: Task Context
// The task context has the following signature
trait TaskContext: // or sometimes it is just called context ;)
  /** externally valid input channel to this task, created dynamically on use */
  // question: unsure if this should be IChannel or OChannel, or perhaps something else
  // what should be the reference to the task.
  val self: IChannel[I]
  /** state of the task */
  def state: TaskState[Any, Any]
  /** emit an event, this is published to the tasks outputs */
  def emit(event: O): Unit
  /** send an event to the provided channel [[ic]] */
  def send[T](ic: IChannel[T], event: T): Unit

  // question: unsure if a task should have the power to do one of the following:
  // - create a new task
  // - create a new workflow
  // - connect two streams
  // - create a new stream
  // - create a new channel between two tasks
  // - close/terminate a task/workflow
  // perhaps it would be nice if we can still argue that workflows are actors in
  // atomic guise.
  // i would suggest that the task can create new tasks and new workflows, and 
  // connect workflows/streams, but not be allowed to arbitrarily connect tasks
  // via channels within the workflow as this may cause a cycle.
  // It might be worth considering what is the minimal set of operations the task
  // needs in order to implement the above mentioned interface, so that we can keep
  // the core model minimal.

  // lib:
  /** request/response, send a request to [[ic]] and expect a response on a
    * freshly created channel
    */
  def ask[T, U](ic: IChannel[T], requestFactory: IChannel[U] => T): Future[U]
  /** await the completion of the provided future */
  def await[T](future: Future[T])(
      cnt: TaskContext[T, O] ?=> T => TaskBehavior[I, O]
  ): TaskBehavior[I, O]
  /** logger */
  def log: Logger

// *********** &&& &&&
// Continue Here::

// Channels 

// Channel combiners perform the synchronization of ticks for the tasks. 
// That is, a combiner can take two channel outputs, and combine them by ensuring
// atom-purity by synchronization of atom-borders.

// Taking 'steps' in the task

```





```scala
// A workflow is a DAG of tasks with sources and sinks.
// Tasks are connected through channels. 
// The workflow executes atoms atomically, i.e. all events within an atom are executed together to completion, and no other atoms are executed concurrently.

// A workflow can be built from tasks and the channels between the tasks.
val wf = Workflow(tasks = ..., channels = ..., sources = ..., sinks = ...)

// A workflow can be created by using a Workflow factory method.
// Here we start a worflow builder.
val wf = Workflows.builder()



```

```scala
// *** Implementation ***

// Everything is implemented on top of "atomic" reactive streams processors, and 
// reactive stream processors (non-atmomic). 
// These are reactive streams that further implement the "atomic" interface.
// The atomic interface...
trait Atomic:
  ???

// Implementation idea:
// TODO: give idea of how things are implemented using the atomic reactive streams processors.
// Streams are implemented as atomic reactive streams processors.
// Workflows and channels are implemented as (non-atomic) reactive streams processors.
```

<!-- ## Commits
A stream should be transactional, that is we can perform a 2-phase-commit to 
ensure that the data is persisted.
```scala
// precommit, returns commit id which can be used to track if commit was executed,
// this is needed in case the client fails, and wants to know later on if the
// commit was executed or not.
stream.precommit()

// block until tick is atomically committed
stream.commit()
// or commit async
stream.commitAsync()
```

```scala
// submit 128 integers to the stream
(0 until 128).foreach({
  stream.submit(_)
})

// trigger a tick
stream.tick()
// after tick tick we can continue submitting events
```

## Example 2
 -->

<!-- ## Example 1: 
- ticks are introduced externally
- ticks trigger a tickHandler on each Source and Task when passing

```scala
// assemble a basic workflow
val wf = Workflows.builder().withName("workflow")
  // source is a stream of Ints
  .source[Int]().withName("src")
  // Identity task, we add a tickHandler to the identity which is triggered by
  // onTick events.
  .identity[Int]().withName("id")
  .withTickHandler( ctx ?=> 
    ctx.log.info("tickHandler triggered")
  )
  .build()

// launch the workflow
system.launch(wf) // could also call this execute

// find the remote workflow
val wf: RemoteWorkflow = system.workflows.discover("workflow").get
val iref: RemoteIRef[Int] = wf.discover("src").get

// establish a short-lived connection to the remote iref
val stream: ORef[Int] = system.streams.connect(iref)

``` -->