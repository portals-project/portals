package portals

import TaskExtensions.*

class FlowBuilderImpl[T, U](using fbctx: FlowBuilderContext[T, U]) extends FlowBuilder[T, U]:
  given WorkflowBuilderContext[T, U] = fbctx.wbctx // used for creating new FlowBuilder instances

  //////////////////////////////////////////////////////////////////////////////
  // Helper methods
  //////////////////////////////////////////////////////////////////////////////
  private def rename(oldName: String, newName: String): FlowBuilder[T, U] =
    // new behavior maps
    if fbctx.wbctx.sources.contains(oldName) then
      // get the behavior, remove old name, add new name
      val behavior = fbctx.wbctx.sources(oldName)
      fbctx.wbctx.sources -= oldName
      fbctx.wbctx.sources += (newName -> behavior)
    else if fbctx.wbctx.sinks.contains(oldName) then
      val behavior = fbctx.wbctx.sinks(oldName)
      fbctx.wbctx.sinks -= oldName
      fbctx.wbctx.sinks += (newName -> behavior)
    else if fbctx.wbctx.tasks.contains(oldName) then
      val behavior = fbctx.wbctx.tasks(oldName)
      fbctx.wbctx.tasks -= oldName
      fbctx.wbctx.tasks += (newName -> behavior)
    else ??? // throw exception, shouldn't happen
    // new connections
    fbctx.wbctx.connections = fbctx.wbctx.connections.map { (from, to) =>
      (from, to) match
        case (l, r) if r == oldName => (l, newName)
        case (l, r) if l == oldName => (newName, r)
        case (l, r)                 => (l, r)
    }
    // rename latest if oldName
    if fbctx.latest == Some(oldName) then FlowBuilder(Some(newName))
    else this

  private def updateTask(name: String, newBehavior: Task[_, _]): FlowBuilder[T, U] =
    fbctx.wbctx.tasks = fbctx.wbctx.tasks - name
    fbctx.wbctx.tasks = fbctx.wbctx.tasks + (name -> newBehavior)
    this

  private[portals] def addConnectionTo(name: String): Unit =
    fbctx.latest match
      case Some(n) =>
        fbctx.wbctx.connections = (n, name) :: fbctx.wbctx.connections
      case None => ???

  private def addTaskFrom[T1, T2](
      behavior: Task[T1 | T2, _],
      from1: FlowBuilder[T1, U],
      from2: FlowBuilder[T2, U]
  ): FlowBuilder[T1 | T2, U] =
    // create name
    val name = fbctx.wbctx.next_task_id()
    // add connection
    from1.asInstanceOf[FlowBuilderImpl[T1, U]].addConnectionTo(name)
    from2.asInstanceOf[FlowBuilderImpl[T1, U]].addConnectionTo(name)
    // add task
    fbctx.wbctx.tasks = fbctx.wbctx.tasks + (name -> behavior)
    // set latest
    given WorkflowBuilderContext[T1 | T2, U] = fbctx.wbctx.asInstanceOf
    FlowBuilder(Some(name))

  private def addTask(behavior: Task[T, _]): FlowBuilder[T, U] =
    // create name
    val name = fbctx.wbctx.next_task_id()
    // add connection
    addConnectionTo(name)
    // add task
    fbctx.wbctx.tasks = fbctx.wbctx.tasks + (name -> behavior)
    // set latest
    FlowBuilder(Some(name))

  private def addSource(_name: String, behavior: Task[T, _]): FlowBuilder[T, U] =
    // create name
    val name = if _name == null then fbctx.wbctx.next_task_id() else _name
    // add connection: no connections for new source :)
    // add task
    fbctx.wbctx.sources = fbctx.wbctx.sources + (name -> behavior)
    // set latest
    FlowBuilder(Some(name))

  private def addSink(_name: String, behavior: Task[U, U]): FlowBuilder[T, U] =
    // create name
    val name = if _name == null then fbctx.wbctx.next_task_id() else _name
    // add connection
    addConnectionTo(name)
    // add sink
    fbctx.wbctx.sinks = fbctx.wbctx.sinks + (name -> behavior)
    // set latest
    FlowBuilder(Some(name))

  //////////////////////////////////////////////////////////////////////////////
  // Builder methods
  //////////////////////////////////////////////////////////////////////////////
  override private[portals] def source[TT >: T <: T](name: String = null): FlowBuilder[T, U] =
    val behavior = Tasks.identity[T]
    addSource(name, behavior).asInstanceOf[FlowBuilder[T, U]]

  override def sink[TT >: T | U <: T & U](name: String = null): FlowBuilder[T, U] =
    val behavior = Tasks.identity[U]
    addSink(name, behavior).asInstanceOf[FlowBuilder[T, U]]

  override def union[TT](other: FlowBuilder[TT, U]): FlowBuilder[T | TT, U] =
    val behavior = Tasks.identity[T | TT]
    addTaskFrom[T, TT](behavior, this, other).asInstanceOf[FlowBuilder[T | TT, U]]

  override def map[TT](f: MapTaskContext[T, TT] ?=> T => TT): FlowBuilder[TT, U] =
    val behavior = Tasks.map[T, TT](f)
    addTask(behavior).asInstanceOf[FlowBuilder[TT, U]]

  override def key(f: T => Int): FlowBuilder[T, U] =
    val behavior = Tasks.key[T](f)
    addTask(behavior).asInstanceOf[FlowBuilder[T, U]]

  override def task[TT](taskBehavior: Task[T, TT]): FlowBuilder[TT, U] =
    val behavior = taskBehavior
    addTask(behavior).asInstanceOf[FlowBuilder[TT, U]]

  override def processor[TT](f: TaskContext[T, TT] ?=> T => Unit): FlowBuilder[TT, U] =
    val behavior = Tasks.processor[T, TT](f)
    addTask(behavior).asInstanceOf[FlowBuilder[TT, U]]

  override def flatMap[TT](f: MapTaskContext[T, TT] ?=> T => Seq[TT]): FlowBuilder[TT, U] =
    val behavior = Tasks.flatMap[T, TT](f)
    addTask(behavior).asInstanceOf[FlowBuilder[TT, U]]

  override def identity(): FlowBuilder[T, U] =
    val behavior = Tasks.identity[T]
    addTask(behavior).asInstanceOf[FlowBuilder[T, U]]

  override def logger(prefix: String = ""): FlowBuilder[T, U] =
    val behavior = Tasks.processor[T, T] { ctx ?=> e =>
      ctx.log.info(prefix + e)
      ctx.emit(e)
    }
    addTask(behavior).asInstanceOf[FlowBuilder[T, U]]

  override def checkExpectedType[TT >: T <: T, UU >: U <: U](): FlowBuilder[T, U] = this

  override def withName(name: String): FlowBuilder[T, U] =
    val oldName = fbctx.latest.get
    rename(oldName, name).asInstanceOf[FlowBuilder[T, U]]

  override def withOnNext(_onNext: TaskContext[T, U] ?=> T => Task[T, U]): FlowBuilder[T, U] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[T, U]]
    val newBehavior = behavior.withOnNext(_onNext)
    updateTask(name, newBehavior).asInstanceOf[FlowBuilder[T, U]]

  override def withOnError(_onError: TaskContext[T, U] ?=> Throwable => Task[T, U]): FlowBuilder[T, U] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[T, U]]
    val newBehavior = behavior.withOnError(_onError)
    updateTask(name, newBehavior).asInstanceOf[FlowBuilder[T, U]]

  override def withOnComplete(_onComplete: TaskContext[T, U] ?=> Task[T, U]): FlowBuilder[T, U] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[T, U]]
    val newBehavior = behavior.withOnComplete(_onComplete)
    updateTask(name, newBehavior).asInstanceOf[FlowBuilder[T, U]]

  override def withOnAtomComplete(_onAtomComplete: TaskContext[T, U] ?=> Task[T, U]): FlowBuilder[T, U] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[T, U]]
    val newBehavior = behavior.withOnAtomComplete(_onAtomComplete)
    updateTask(name, newBehavior).asInstanceOf[FlowBuilder[T, U]]

end FlowBuilderImpl //
