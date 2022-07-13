package portals

import java.util.concurrent.locks.ReentrantLock

class AtomicStreamImpl[I, O](workflow: WorkflowBuilder) extends AtomicStream[I, O] :
  private def addTask(name: String, behavior: TaskBehavior[_, _]): AtomicStream[I, O] =
    if latest.isDefined then
      workflow.connections = workflow.connections :+ (latest.get, name)
    workflow.tasks = workflow.tasks + (name -> behavior)
    val newStream = new AtomicStreamImpl[I, O](workflow)
    newStream.latest = Some(name)
    newStream

  private def addTask(behavior: TaskBehavior[_, _]): AtomicStream[I, O] =
    addTask(workflow.task_id(), behavior)

  private def updateTask(name: String, newBehavior: TaskBehavior[_, _]): Unit =
    workflow.tasks = workflow.tasks - name
    workflow.tasks = workflow.tasks + (name -> newBehavior)

  private[portals] def source[T](): AtomicStream[Nothing, T] =
    val behavior = TaskBehaviors.identity[T]
    val name = workflow.task_id()
    workflow.sources = workflow.sources + (name -> behavior)
    addTask(name, behavior).asInstanceOf[AtomicStream[Nothing, T]]

  private[portals] def from[I, O](fb: AtomicStream[I, O]): AtomicStream[Nothing, O] =
    fb.asInstanceOf[AtomicStream[Nothing, O]]

  private[portals] def merge[I1, I2, O](fb1: AtomicStream[I1, O], fb2: AtomicStream[I2, O]): AtomicStream[Nothing, O] =
    val behavior = TaskBehaviors.identity[O]
    val newStream = addTask(behavior)
    workflow.connections = workflow.connections :+ (fb1.latest.get, newStream.latest.get)
    workflow.connections = workflow.connections :+ (fb2.latest.get, newStream.latest.get)
    newStream.asInstanceOf[AtomicStream[Nothing, O]]

  private[portals] def cycle[T](): AtomicStream[T, T] =
    val behavior = TaskBehaviors.identity[T]
    val newStream = addTask(behavior)
    newStream.cycleIn = Some(newStream.latest.get)
    newStream.asInstanceOf[AtomicStream[T, T]]

  def sink[OO >: O <: O](): AtomicStream[I, Nothing] =
    val behavior = TaskBehaviors.identity[I]
    val name = workflow.task_id()
    workflow.sinks = workflow.sinks + (name -> behavior)
    addTask(name, behavior).asInstanceOf[AtomicStream[I, Nothing]]

  def intoCycle(fb: AtomicStream[O, O]): AtomicStream[I, Nothing] =
    fb.cycleIn match
      case Some(into) =>
        workflow.connections = workflow.connections :+ (latest.get, into)
        this.asInstanceOf[AtomicStream[I, Nothing]]
      case None => ??? // shouldn't intoCycle if cycle entry does not exist

  def identity(): AtomicStream[I, O] =
    val behavior = TaskBehaviors.identity[O]
    addTask(behavior).asInstanceOf[AtomicStream[I, O]]

  def keyBy[T](f: O => T): AtomicStream[I, O] =
    processor[O] { ctx ?=> event => {
      ctx.key = Key(f(event).hashCode());
      ctx.emit(event)
    }
    }

  def map[T](f: ReducedTaskContext[O, T] ?=> O => T): AtomicStream[I, T] =
    val behavior = TaskBehaviors.map[O, T](f)
    addTask(behavior).asInstanceOf[AtomicStream[I, T]]

  def behavior[T](b: TaskBehavior[O, T]): AtomicStream[I, T] =
    addTask(b).asInstanceOf[AtomicStream[I, T]]

  def vsm[T](b: TaskBehavior[O, T]): AtomicStream[I, T] =
    addTask(b).asInstanceOf[AtomicStream[I, T]]

  def processor[T](f: TaskContext[O, T] ?=> O => Unit): AtomicStream[I, T] =
    val behavior = TaskBehaviors.processor[O, T](f)
    addTask(behavior).asInstanceOf[AtomicStream[I, T]]

  def flatMap[T](f: ReducedTaskContext[O, T] ?=> O => Seq[T]): AtomicStream[I, T] =
    val behavior = TaskBehaviors.flatMap[O, T](f)
    addTask(behavior).asInstanceOf[AtomicStream[I, T]]

  def withName(name: String): AtomicStream[I, O] =
    val oldName = latest.get
    val behavior = workflow.tasks(oldName)
    workflow.tasks = workflow.tasks.removed(oldName)
    // using addTask here breaks as it connects to itself
    workflow.tasks = workflow.tasks + (name -> behavior)
    if (workflow.sources.contains(oldName)) {
      workflow.sources = workflow.sources.removed(oldName)
      workflow.sources = workflow.sources + (name -> behavior)
    }
    if (workflow.sinks.contains(oldName)) {
      workflow.sinks = workflow.sinks.removed(oldName)
      workflow.sinks = workflow.sinks + (name -> behavior)
    }
    latest = Some(name)
    workflow.connections = workflow.connections.map { (from, to) =>
      (from, to) match
        case (l, r) if r == oldName => (l, name)
        case (l, r) if l == oldName => (name, r)
        case (l, r) => (l, r)
    }
    this.asInstanceOf[AtomicStream[I, O]]

  def withLogger(prefix: String = ""): AtomicStream[I, O] =
    val behavior = TaskBehaviors.processor[O, O] { ctx ?=> x =>
      ctx.log.info(prefix + x)
      ctx.emit(x)
    }
    addTask(behavior).asInstanceOf[AtomicStream[I, O]]

  def withOnNext(_onNext: TaskContext[I, O] ?=> I => TaskBehavior[I, O]): AtomicStream[I, O] =
    // TODO: consider implementing factories for these in the TaskBehaviors file
    val name = latest.get
    val behavior = workflow.tasks(name).asInstanceOf[TaskBehavior[I, O]]
    val newBehavior = new TaskBehavior[I, O] {
      override def onNext(ctx: TaskContext[I, O])(t: I): TaskBehavior[I, O] =
        _onNext(using ctx)(t)

      override def onError(ctx: TaskContext[I, O])(t: Throwable): TaskBehavior[I, O] =
        behavior.onError(ctx)(t)

      override def onComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] =
        behavior.onComplete(ctx)

      override def onAtomComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] =
        behavior.onAtomComplete(ctx)
    }
    updateTask(name, newBehavior)
    this.asInstanceOf[AtomicStream[I, O]]

  def withOnError(_onError: TaskContext[I, O] ?=> Throwable => TaskBehavior[I, O]): AtomicStream[I, O] =
    val name = latest.get
    val behavior = workflow.tasks(name).asInstanceOf[TaskBehavior[I, O]]
    val newBehavior = new TaskBehavior[I, O] {
      override def onNext(ctx: TaskContext[I, O])(t: I): TaskBehavior[I, O] =
        behavior.onNext(ctx)(t)

      override def onError(ctx: TaskContext[I, O])(t: Throwable): TaskBehavior[I, O] =
        _onError(using ctx)(t)

      override def onComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] =
        behavior.onComplete(ctx)

      override def onAtomComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] =
        behavior.onAtomComplete(ctx)
    }
    updateTask(name, newBehavior)
    this.asInstanceOf[AtomicStream[I, O]]

  def withOnComplete(_onComplete: TaskContext[I, O] ?=> TaskBehavior[I, O]): AtomicStream[I, O] =
    val name = latest.get
    val behavior = workflow.tasks(name).asInstanceOf[TaskBehavior[I, O]]
    val newBehavior = new TaskBehavior[I, O] {
      override def onNext(ctx: TaskContext[I, O])(t: I): TaskBehavior[I, O] =
        behavior.onNext(ctx)(t)

      override def onError(ctx: TaskContext[I, O])(t: Throwable): TaskBehavior[I, O] =
        behavior.onError(ctx)(t)

      override def onComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] =
        _onComplete(using ctx)

      override def onAtomComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] =
        behavior.onAtomComplete(ctx)
    }
    updateTask(name, newBehavior)
    this.asInstanceOf[AtomicStream[I, O]]

  def withOnAtomComplete(_onAtomComplete: TaskContext[I, O] ?=> TaskBehavior[I, O]): AtomicStream[I, O] =
    val name = latest.get
    val behavior = workflow.tasks(name).asInstanceOf[TaskBehavior[I, O]]
    val newBehavior = new TaskBehavior[I, O] {
      override def onNext(ctx: TaskContext[I, O])(t: I): TaskBehavior[I, O] =
        behavior.onNext(ctx)(t)

      override def onError(ctx: TaskContext[I, O])(t: Throwable): TaskBehavior[I, O] =
        behavior.onError(ctx)(t)

      override def onComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] =
        behavior.onComplete(ctx)

      override def onAtomComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] =
        _onAtomComplete(using ctx)
    }
    updateTask(name, newBehavior)
    this.asInstanceOf[AtomicStream[I, O]]

  def checkExpectedType[OO >: O <: O](): AtomicStream[I, O] = this

end AtomicStreamImpl