package pods.workflows

trait AtomicStream[I, O]:
  private[pods] var cycleIn: Option[String] = None
  private[pods] var latest: Option[String] = None

  private[pods] def source[T](): AtomicStream[Nothing, T]

  private[pods] def from[I, O](fb: AtomicStream[I, O]): AtomicStream[Nothing, O]

  private[pods] def merge[I1, I2, O](fb1: AtomicStream[I1, O], fb2: AtomicStream[I2, O]): AtomicStream[Nothing, O]

  private[pods] def cycle[T](): AtomicStream[T, T]

  def sink[OO >: O <: O](): AtomicStream[I, Nothing]

  def identity(): AtomicStream[I, O]

  def intoCycle(wfb: AtomicStream[O, O]): AtomicStream[I, Nothing]

  def keyBy[T](f: O => T): AtomicStream[I, O]

  def map[T](f: AttenuatedTaskContext[O, T] ?=> O => T): AtomicStream[I, T]

  def behavior[T](b: TaskBehavior[O, T]): AtomicStream[I, T]

  def vsm[T](b: TaskBehavior[O, T]): AtomicStream[I, T]

  def processor[T](f: TaskContext[O, T] ?=> O => Unit): AtomicStream[I, T]

  def flatMap[T](f: AttenuatedTaskContext[O, T] ?=> O => Seq[T]): AtomicStream[I, T]

  def withName(name: String): AtomicStream[I, O]

  def withLogger(prefix: String = ""): AtomicStream[I, O]

  def withOnNext(_onNext: TaskContext[I, O] ?=> I => TaskBehavior[I, O]): AtomicStream[I, O]

  def withOnError(_onError: TaskContext[I, O] ?=> Throwable => TaskBehavior[I, O]): AtomicStream[I, O]

  def withOnComplete(_onComplete: TaskContext[I, O] ?=> TaskBehavior[I, O]): AtomicStream[I, O]

  def withOnAtomComplete(_onAtomComplete: TaskContext[I, O] ?=> TaskBehavior[I, O]): AtomicStream[I, O]

  /** Check the current type against the provided expected type.
   *
   * Compares FlowBuilder[I, O] with FlowBuilder[II, OO], will succeed if
   * I <: II <: I and O <: OO <: O.
   */
  def checkExpectedType[OO >: O <: O](): AtomicStream[I, O]

end AtomicStream