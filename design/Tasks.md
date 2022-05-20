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
    // alternative: The VSM factory could be called VSM instead of Tasks, that would make more sense.
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
// be built from stronger primitives such as the processor. It also supplies a 
// factory from a taskBehavior, as it is convenient.

// The base task context has the following signature
trait TaskContext[I, O]: // or sometimes it is just called context ;)
  // Base operations
  /** The main input channel of this task */
  private[pods] val iref: IChannel[I]
  /** The main output channel of this task */
  private[pods] val oref: OChannel[O]
  /** state of the task */
  def state: TaskState[Any, Any]
  /** emit an event, this is published to the tasks outputs */
  def emit(event: O): Unit
  
  // Dynamic communication operations (actor-like)
  /** externally valid input channel to this task, created dynamically on use */
  val selfI: IChannel[I] // note: this channel is connected indirectly via the iref channel
  /** externally valid output channel to this task, created dynamically on use */
  val selfO: OChannel[O] // note: this channel is connected indirectly via the iref channel
  /** send an event to the provided channel [[ic]] */
  def send[T](ic: IChannel[T], event: T): Unit

  // Atom operations
  /** finishes the ongoing atom and starts a new tick */
  def tick:(): Unit // or fuse()
  
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

  // Dynamic structure operations cntd.
  // Omitted, even though needed for claim that our model is an actor model.
  /** create/terminate a task */ 
  /** create/terminate a workflow */ 

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

// *** Channels ***
// Tasks are combined via channels.
// This can be achieved by using the connect factory, that subscribes the main input
// of task1 to the main output of task2.
val channel = Channels.connect(task1, task2)

// Channel combiners perform the synchronization of ticks for the tasks. 
// That is, a combiner can take two channel outputs, and combine them by ensuring
// atom-purity by synchronization of atom-borders.

// Lib: Taking 'steps' in the task
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
