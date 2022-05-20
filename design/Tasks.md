# Tasks

```scala
// A task behavior implements the following trait.
trait Task[I, O]:
  // The context is swapped during runtime
  private[pods] var ctx: Context = null
  def onSubscribe(s: Subscription): Unit
  def onNext(t: I): Unit
  def onError(t: Throwable): Unit
  def onComplete(): Unit
  def onAtomComplete(): Unit // or def onTick(): Unit

// We can create a task using a Task factory method.
// The vsm factory is the most powerful factory, it creates a per-key 
// virtual-state-machine task.
val task: Task = Tasks.vsm { ctx ?=> event =>
    // do something with the event
    // ...
    // choose behavior for next receive
    Tasks.same // similar mechanism as Akka typed
  }
  // further extension methods allow us to modify the members of the task
  .withOnSubscribe( ctx ?=> subscription => ... )
  .withOnNext(...)
  .withOnError(...)
  .withOnComplete(...)
  .withOnAtomComplete(...)

// We should note here that in Scala 3 it is neat that the contextual parameter
// (contextual params use ?=>) can be omitted like the following, this will
// just the way we expect it to:
// Alternative: The VSM factory could be called VSM instead of Tasks, that would make more sense.
val task = Tasks.vsm { event => 
    // we can still access the context from the implicit scope
    summon[Context].log.info("hello world") 
    // although it probably is nicer to expose this as a method in a DSL instead
    import pods.DSL.*
    ctx.log.info("hello world") // ctx is a method call that summons the Context
    Tasks.same // don't forget to return a behavior :)
  }
// we can even omit the "event =>" to use the pattern matchin cases straight away:
val task = Tasks.vsm { // no need for "event =>" 
  case Event1() => ...
}

// There are many more factory methods available, as most situations don't require
// a virtual state machine abstraction.
// The processor factory gets the ctx object together with the event, but does
// not require us to return the behavior for the next receive.
val identity = Tasks.processor { ctx ?=> event =>
    ctx.emit(event)
  }
// Similarly, we have map, flatMap, etc. available.

// The task context implements the following trait.
trait Context[I, O]:
  // Base operations
  /** The main input channel of this task */
  private[pods] val mainiref: IChannel[I]
  /** The main output channel of this task */
  private[pods] val mainoref: OChannel[O]
  /** state of the task */
  def state: TaskState[Any, Any]
  /** emit an event, this is published to the main task output */
  def emit(event: O): Unit
  /** logger */
  def log: Logger

  // Ask + await
  /** request/response, send a request to [[ic]] and expect a response on a freshly created channel */
  def ask[T, U](ic: IChannel[T], requestFactory: IChannel[U] => T): Future[U]
  /** await the completion of the provided future */
  def await[T](future: Future[T])(
      cnt: Context[T, O] ?=> T => TaskBehavior[I, O]
  ): TaskBehavior[I, O]

  // Dynamic communication operations (actor-like)
  /** externally valid input channel to this task, created dynamically on use */
  val iref: IChannel[I] // note: this channel is connected indirectly via the iref channel
  /** externally valid output channel to this task, created dynamically on use */
  val oref: OChannel[O] // note: this channel is connected indirectly via the oref channel
  /** send an event to the provided channel */
  def send[T](ic: IChannel[T], event: T): Unit

  // Atom operations
  /** finishes the ongoing atom and starts a new tick */
  def fuse:(): Unit // or tick()
  
  // Dynamic structure operations
  /** create and close a new externally valid input/output stream/channel to this task */ 
  private[pods] def createIRef[T](): IChannel[T]
  private[pods] def closeIRef[T](ic: IChannel[T]): Unit
  private[pods] def createORef[T](): OChannel[T]
  private[pods] def closeORef[T](oc: OChannel[T]): Unit
  /** connect streams and channels */
  private[pods] def connect[T, S](ic: IChannel[T], oc: OChannel[S]): Unit
  private[pods] def connect[T, S](is: IStream[T], os: OStream[S]): Unit
  /** submit an event to the provided output channel [[oc]] */
  private[pods] def submit[T](oc: OChannel[T], event: T): Unit
  /** omitted: create/terminate a task */ 
  /** omitted: create/terminate a workflow */ 

// We can take steps in a task across atoms.
// This is achieved by using the withStep( ... ) method on a task.
val task = Tasks.vsm { ctx ?=> event =>
  ...
}.withStep { ctx ?=> event =>
  ...
}.withLoop(10) { ctx ?=> event =>
  ...
}

```
