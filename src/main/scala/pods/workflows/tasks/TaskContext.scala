package pods.workflows

import java.util.concurrent.SubmissionPublisher

/** TaskContext */
private[pods] trait TaskContext[I, O]:
  //////////////////////////////////////////////////////////////////////////////
  // Internals
  //////////////////////////////////////////////////////////////////////////////

  private[pods] var submitter: SubmissionPublisher[O]

  /** The main input channel of this task, for internal use */
  private[pods] var mainiref: IStreamRef[I]

  /** The main output channel of this task, for internal use */
  private[pods] var mainoref: OStreamRef[O]

  //////////////////////////////////////////////////////////////////////////////
  // Execution Context
  //////////////////////////////////////////////////////////////////////////////

  /** Contextual key for per-key execution */
  // note: should be var so that it can be swapped at runtime
  private[pods] var key: Key[Int]

  /** The `SystemContext` that this task belongs to */
  // note: should be var so that it can be swapped at runtime
  private[pods] var system: SystemContext

  //////////////////////////////////////////////////////////////////////////////
  // Base Operations
  //////////////////////////////////////////////////////////////////////////////

  /** State of the task */
  // TODO: make typed instead of Any
  def state: TaskState[Any, Any]

  /** Emit an event */
  def emit(event: O): Unit

  /** Logger */
  def log: Logger

  //////////////////////////////////////////////////////////////////////////////
  // Ask Await
  //////////////////////////////////////////////////////////////////////////////

  /** Request/response, send a request to `ic` and expect a response on a freshly created stream */
  def ask[T, U](iref: IStreamRef[T], requestFactory: IStreamRef[U] => T): Future[U]

  /** Await the completion of the provided future */
  def await[T](future: Future[T])(cont: TaskContext[T, O] ?=> T => TaskBehavior[I, O]): TaskBehavior[I, O]

  //////////////////////////////////////////////////////////////////////////////
  // Atom Operations
  //////////////////////////////////////////////////////////////////////////////

  /** finishes the ongoing atom and starts a new tick */
  def fuse(): Unit // or tick()

  //////////////////////////////////////////////////////////////////////////////
  // Dynamic Communication
  //////////////////////////////////////////////////////////////////////////////

  /** Externally referencable input stream to this task. */
  // note: this stream is connected indirectly via mainiref
  // note: lazily created on use
  private[pods] var _iref: IStreamRef[I]
  def iref: IStreamRef[I]

  /** Externally referencable output stream to this task. */
  // note: this stream is connected indirectly via mainoref
  // note: lazily created on use
  private[pods] var _oref: OStreamRef[O]
  def oref: OStreamRef[O]

  /** Send an event to the provided stream */
  // note: this creates a new OStreamRef that is then connected to the 
  // IStreamRef and used to submit the event
  def send[T](iref: IStreamRef[T], event: T): Unit

  //////////////////////////////////////////////////////////////////////////////
  // Dynamic Structure
  //////////////////////////////////////////////////////////////////////////////

  // /** Omitted: Create an IRef handle that publishes events to this task */
  // def createIRef[T](): IStreamRef[T] 

  // /** Omitted: Create an ORef handle that subscribes to events of this task */
  // def createORef[T](): OStreamRef[T]

  // The following are omitted, consider adding back if functionality is required

  /** Omitted: create an IStreamRef OStreamRef pair */

  /** Omitted: connect an IStreamRef OStreamRef pair */

  /** Omitted: create/terminate a new task */
    
  /** Omitted: create/terminate a new workflow */ 

object TaskContext:
  def apply[I, O](): TaskContext[I, O] = new TaskContextImpl[I, O]