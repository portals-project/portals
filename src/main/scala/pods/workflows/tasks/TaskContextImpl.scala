package pods.workflows

private[pods] class TaskContextImpl[I, O] extends TaskContext[I, O]:
  private[pods] var submitter: Submitter[O] = null
  
  private[pods] var mainiref: IStreamRef[I] = null

  private[pods] var mainoref: OStreamRef[O] = null

  private[pods] var key: Key[Int] = null

  private[pods] var system: SystemContext = null

  val state: TaskState[Any, Any] = 
    TaskState()

  def emit(event: O): Unit =
    submitter.submit(event)

  def log: Logger = 
    Logger(this.getClass().toString())

  def ask[T, U](iref: IStreamRef[T], requestFactory: IStreamRef[U] => T): Future[U] = 
    ???

  def await[T](future: Future[T])(cont: TaskContext[T, O] ?=> T => TaskBehavior[I, O]): TaskBehavior[I, O] = 
    ???

  def fuse(): Unit =
    submitter.fuse()

  private[pods] var _iref: IStreamRef[I] = null
  def iref: IStreamRef[I] = 
    _iref

  private[pods] var _oref: OStreamRef[O] = null
  def oref: OStreamRef[O] = 
    _oref

  def send[T](iref: IStreamRef[T], event: T): Unit = 
    iref.submit(event)
