# Tasks

```scala
// A task behavior implements the following trait.
trait TaskBehavior[I, O]:
  // The context is swapped during runtime
  private[pods] var ctx: Context = null
  // TODO: consider removing onSubscribe as it is part of the low-level API
  def onSubscribe(s: Subscription): Unit 
  def onNext(t: I): Unit
  def onError(t: Throwable): Unit
  def onComplete(): Unit
  def onAtomComplete(): Unit // or def onTick(): Unit

// We can create a task using a Task factory method.
// The vsm factory is the most powerful factory, it creates a per-key 
// virtual-state-machine task.
// Alternative: we could call the factory `Tasks` instead of `TaskBehaviors`
val task: Task = TaskBehaviors.vsm { ctx ?=> event =>
    // do something with the event
    // ...
    // choose behavior for next receive
    TaskBehaviors.same // similar mechanism as Akka typed
  }
  // further extension methods allow us to modify the members of the task
  .withOnSubscribe( ctx ?=> subscription => ... )
  .withOnNext(...)
  .withOnError(...)
  .withOnComplete(...)
  .withOnAtomComplete(...)

// We should note here that in Scala 3 it is neat that the contextual parameter
// (contextual params use ?=>) can be omitted like the following, this will work
// just the way we expect it to:
val task = TaskBehaviors.vsm { event => 
    // we can still access the context from the implicit scope
    summon[Context].log.info("hello world") 
    // although it probably is nicer to expose this as a method in a DSL instead
    import pods.DSL.ctx // object DSL { def ctx = summon[Context] }
    ctx.log.info("hello world") // ctx is a method call that summons the Context
    TaskBehaviors.same // don't forget to return a behavior :)
  }
// we can even omit the "event =>" to use the pattern matchin cases straight away:
val task = TaskBehaviors.vsm { // no need for "event =>" 
  case Event1() => ???
}

// There are many more factory methods available, as most situations don't require
// a virtual state machine abstraction.
// The processor factory gets the ctx object together with the event, but does
// not require us to return the behavior for the next receive.
val identity = TaskBehaviors.processor { ctx ?=> event =>
    ctx.emit(event)
  }
// Similarly, we have map, flatMap, etc. available.
val mapper = TaskBehaviors.map( _.toString() )
val flatMapper = TaskBehaviors.flatMap( List( _.toString() ) )
...

// A task has access to some task context, and this task context describes
// what actions the task can perform during the handling of an event.

// The task context implements the following trait.
trait TaskContext[I, O]:
  /** Contextual key for per-key execution, can be swapped at runtime. */
  private[pods] var key

  // Direct input and output streams of the task
  /** The main input channel of this task */
  private[pods] val mainiref: IStreamRef[I]
  /** The main output channel of this task */
  private[pods] val mainoref: OStreamRef[O]
  
  // Base operations
  /** state of the task */
  // TODO: make this typed
  def state: TaskState[Any, Any]
  /** emit an event, this is published to the tasks main output mainoref */
  def emit(event: O): Unit
  /** logger */
  def log: Logger

  // Ask + await
  /** request/response, send a request to [[ic]] and expect a response on a freshly created channel */
  def ask[T, U](ic: IStreamRef[T], requestFactory: IStreamRef[U] => T): Future[U]
  /** await the completion of the provided future */
  def await[T](future: Future[T])(
      cnt: Context[T, O] ?=> T => TaskBehavior[I, O]
  ): TaskBehavior[I, O]

  // Dynamic communication operations (actor-like)
  // Note: lazily created on use
  /** externally valid input channel to this task, created dynamically on use */
  val iref: IStreamRef[I] // note: this channel is connected indirectly via mainiref
  /** externally valid output channel to this task, created dynamically on use */
  val oref: OStreamRef[O] // note: this channel is connected indirectly via mainoref
  
  /** send an event to the provided channel */
  def send[T](ic: IStreamRef[T], event: T): Unit

  // Atom operations
  /** finishes the ongoing atom and starts a new tick */
  def fuse:(): Unit // or tick()
  
  // Dynamic structure operations
  /** create and close a new externally valid input/output stream to this task */ 
  /** create a IStreamRef that publishes to this task's mainiref. */
  // Discussion: these are mighty powerful, and should remain private perhaps?
  private[pods] def createIRef[T](): IStreamRef[T] 
  private[pods] def closeIRef[T](ic: IStreamRef[T]): Unit
  /** creates a OStreamRef that subscribes to this task's mainoref. */
  private[pods] def createORef[T](): OStreamRef[T]
  private[pods] def closeORef[T](oc: OStreamRef[T]): Unit
  /** create a IStreamRef OStreamRef pair */
  // TODO: one of these should be owned by the task
  private[pods] def createStream[T](): (IStreamRef[T], OStreamRef[T])
  /** connect streams */
  private[pods] def connect[T, S](ic: IStreamRef[T], oc: OStreamRef[S]): Unit
  /** omitted: create/terminate a task */ 
  /** omitted: create/terminate a workflow */ 


// Another stateful combinator is to take steps in a task across atoms.
// That is, the task should change it's behavior for handling the next atom
// acording as specified by the behavior provided by withStep.
// This is achieved by using the withStep( ... ) method on a task.
val task = Tasks.vsm { ctx ?=> event =>
  ... // some behavior for first atom
}
.withStep { ctx ?=> event =>
  ... // then, some new behavior for the next atom
}
.withLoop(10) { ctx ?=> event =>
  ... // then, some behavior for the next 10 atoms
}

```
