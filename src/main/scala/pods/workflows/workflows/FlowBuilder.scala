package pods.workflows

trait FlowBuilder[I, O]:
  private[pods] var cycleIn: Option[String] = None
  private[pods] var latest: Option[String] = None

  private[pods] def source[T](): FlowBuilder[Nothing, T]

  private[pods] def from[I, O](fb: FlowBuilder[I, O]): FlowBuilder[Nothing, O]

  private[pods] def merge[I1, I2, O](fb1: FlowBuilder[I1, O], fb2: FlowBuilder[I2, O]): FlowBuilder[Nothing, O]

  private[pods] def cycle[T](): FlowBuilder[T, T]

  def sink(): FlowBuilder[I, Nothing]

  def intoCycle(wfb: FlowBuilder[O, O]): FlowBuilder[I, Nothing]

  def map[T](f: O => T): FlowBuilder[I, T]

  def behavior[T](b: TaskBehavior[O, T]): FlowBuilder[I, T]

  def vsm[T](b: TaskBehavior[O, T]): FlowBuilder[I, T]

  def processor[T](f: TaskContext[O, T] ?=> O => Unit): FlowBuilder[I, T]

  def flatMap[T](f: O => Seq[T]): FlowBuilder[I, T] 

  def withName(name: String): FlowBuilder[I, O]
  
  def withLogger(prefix: String = ""): FlowBuilder[I, O]

  def withOnNext(_onNext: TaskContext[I, O] ?=> I => TaskBehavior[I, O]): FlowBuilder[I, O]

  def withOnError(_onError: TaskContext[I, O] ?=> Throwable => TaskBehavior[I, O]): FlowBuilder[I, O]
  
  def withOnComplete(_onComplete: TaskContext[I, O] ?=> TaskBehavior[I, O]): FlowBuilder[I, O]
  
  def withOnAtomComplete(_onAtomComplete: TaskContext[I, O] ?=> TaskBehavior[I, O]): FlowBuilder[I, O]

end FlowBuilder