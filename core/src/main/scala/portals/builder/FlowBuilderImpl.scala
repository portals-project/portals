package portals

import scala.annotation.targetName

class FlowBuilderImpl[T, U, CT, CU](using fbctx: FlowBuilderContext[T, U]) extends FlowBuilder[T, U, CT, CU]:
  given WorkflowBuilderContext[T, U] = fbctx.wbctx // used for creating new FlowBuilder instances

  //////////////////////////////////////////////////////////////////////////////
  // Helper methods
  //////////////////////////////////////////////////////////////////////////////
  private def rename(oldName: String, newName: String): FlowBuilder[T, U, CT, CU] =
    // new behavior maps
    if fbctx.wbctx.tasks.contains(oldName) then
      val behavior = fbctx.wbctx.tasks(oldName)
      fbctx.wbctx.tasks -= oldName
      fbctx.wbctx.tasks += (newName -> behavior)
    else ??? // throw exception, shouldn't happen
    // new connections
    fbctx.wbctx.connections = fbctx.wbctx.connections.map { (from, to) =>
      (from, to) match
        case (l, r) if r == oldName => (l, newName)
        case (l, r) if l == oldName => (newName, r)
        case (l, r) => (l, r)
    }
    // rename latest if oldName
    if fbctx.latest == Some(oldName) then FlowBuilder(Some(newName))
    else this

  private def updateTask(name: String, newBehavior: Task[_, _]): FlowBuilder[T, U, CT, CU] =
    fbctx.wbctx.tasks = fbctx.wbctx.tasks - name
    fbctx.wbctx.tasks = fbctx.wbctx.tasks + (name -> newBehavior)
    this

  // adds connection from latest task to the provided name
  private[portals] def addConnectionTo(name: String): Unit =
    fbctx.latest match
      case Some(n) =>
        fbctx.wbctx.connections = (n, name) :: fbctx.wbctx.connections
      case None => ???

  private def addTaskFrom[CU1, CU2, CT1, CT2, CCU](
      behavior: Task[CU1 | CU2, CCU],
      from1: FlowBuilder[T, U, CT1, CU1],
      from2: FlowBuilder[T, U, CT2, CU2]
  ): FlowBuilder[T, U, CU1 | CU2, CCU] =
    // create name
    val name = fbctx.wbctx.bctx.next_id()
    // add connection
    from1.asInstanceOf[FlowBuilderImpl[T, U, CT1, CU1]].addConnectionTo(name)
    from2.asInstanceOf[FlowBuilderImpl[T, U, CT2, CU2]].addConnectionTo(name)
    // add task
    fbctx.wbctx.tasks = fbctx.wbctx.tasks + (name -> behavior)
    // set latest
    FlowBuilder(Some(name))

  private def addTaskFrom2[CU, CCU](
      behavior: Task[CU, CCU],
      froms: List[FlowBuilder[T, U, _, CU]],
  ): FlowBuilder[T, U, CU, CCU] =
    // create name
    val name = fbctx.wbctx.bctx.next_id()
    // add connections
    froms.foreach { from =>
      from.asInstanceOf[FlowBuilderImpl[T, U, _, CU]].addConnectionTo(name)
    }
    // add task
    fbctx.wbctx.tasks = fbctx.wbctx.tasks + (name -> behavior)
    // set latest
    FlowBuilder(Some(name))

  private def addTask[CCU](behavior: Task[CU, CCU]): FlowBuilder[T, U, CU, CCU] =
    // create name
    val name = fbctx.wbctx.bctx.name_or_id(null)
    // add connection
    addConnectionTo(name)
    // add task
    fbctx.wbctx.tasks = fbctx.wbctx.tasks + (name -> behavior)
    // set latest
    FlowBuilder(Some(name))

  private def addSource(): FlowBuilder[T, U, T, T] =
    fbctx.wbctx.source match
      case Some(source) =>
        // set latest
        FlowBuilder(Some(source))
      case None =>
        // create name
        val name = fbctx.wbctx.bctx.name_or_id()
        // add connection: no connections for new source :)
        // add source
        fbctx.wbctx.source = Some(name)
        // set latest
        FlowBuilder(Some(name))

  private def addSink(): FlowBuilder[T, U, U, U] =
    fbctx.wbctx.sink match
      case Some(sink) =>
        // add connection
        addConnectionTo(sink)
        // set latest
        FlowBuilder(None)
      case None =>
        // create name
        val name = fbctx.wbctx.bctx.name_or_id()
        // add connection
        addConnectionTo(name)
        // add sink
        fbctx.wbctx.sink = Some(name)
        // set latest
        FlowBuilder(None)

  //////////////////////////////////////////////////////////////////////////////
  // Builder methods
  //////////////////////////////////////////////////////////////////////////////

  override def freeze(): Workflow[T, U] =
    fbctx.wbctx.freeze()

  override private[portals] def source[CC >: T <: T](ref: AtomicStreamRefKind[T]): FlowBuilder[T, U, CC, CC] =
    fbctx.wbctx.consumes = ref
    addSource()

  override def sink[CC >: CU | U <: CU & U](): FlowBuilder[T, U, U, U] =
    addSink()

  // TODO: deprecated
  // override def union[CCT, CCU](other: FlowBuilder[T, U, CCT, CCU]): FlowBuilder[T, U, CCU | CU, CCU | CU] =
  //   val behavior = Tasks.identity[CU | CCU]
  //   addTaskFrom[CU, CCU, CT, CCT, CU | CCU](behavior, this, other)

  override def union(others: List[FlowBuilder[T, U, _, CU]]): FlowBuilder[T, U, CU, CU] =
    val behavior = Tasks.identity[CU]
    addTaskFrom2(behavior, this :: others)

  override def from[CU, CCU](others: FlowBuilder[T, U, _, CU]*)(task: Task[CU, CCU]): FlowBuilder[T, U, CU, CCU] =
    addTaskFrom2[CU, CCU](task, others.toList)

  override def map[CCU](f: MapTaskContext[CU, CCU] ?=> CU => CCU): FlowBuilder[T, U, CU, CCU] =
    val behavior = Tasks.map[CU, CCU](f)
    addTask(behavior)

  override def key(f: CU => Int): FlowBuilder[T, U, CU, CU] =
    val behavior = Tasks.key[CU](f)
    addTask(behavior)

  override def task[CCU](taskBehavior: Task[CU, CCU]): FlowBuilder[T, U, CU, CCU] =
    val behavior = taskBehavior
    addTask(behavior)

  override def processor[CCU](f: TaskContext[CU, CCU] ?=> CU => Unit): FlowBuilder[T, U, CU, CCU] =
    val behavior = Tasks.processor[CU, CCU](f)
    addTask(behavior)

  override def flatMap[CCU](f: MapTaskContext[CU, CCU] ?=> CU => Seq[CCU]): FlowBuilder[T, U, CU, CCU] =
    val behavior = Tasks.flatMap[CU, CCU](f)
    addTask(behavior)

  override def filter(p: CU => Boolean): FlowBuilder[T, U, CU, CU] =
    flatMap { t => if p(t) then Seq(t) else Seq.empty }

  override def vsm[CCU](defaultTask: Task[CU, CCU]): FlowBuilder[T, U, CU, CCU] =
    val behavior = Tasks.vsm(defaultTask)
    addTask(behavior)

  override def init[CCU](initFactory: TaskContext[CU, CCU] ?=> Task[CU, CCU]): FlowBuilder[T, U, CU, CCU] =
    val behavior = Tasks.init(initFactory)
    addTask(behavior)

  override def identity(): FlowBuilder[T, U, CU, CU] =
    val behavior = Tasks.identity[CU]
    addTask(behavior)

  override def logger(prefix: String = ""): FlowBuilder[T, U, CU, CU] =
    val behavior = Tasks.processor[CU, CU] { ctx ?=> e =>
      ctx.log.info(prefix + e)
      ctx.emit(e)
    }
    addTask(behavior)

  override def checkExpectedType[CCU >: CU <: CU](): FlowBuilder[T, U, CT, CU] = this

  override def withName(name: String): FlowBuilder[T, U, CT, CU] =
    val oldName = fbctx.latest.get
    rename(oldName, name)

  override def withOnNext(_onNext: TaskContext[CT, CU] ?=> CT => Task[CT, CU]): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[CT, CU]]
    val newBehavior = behavior.withOnNext(_onNext)
    updateTask(name, newBehavior)

  override def withOnError(_onError: TaskContext[CT, CU] ?=> Throwable => Task[CT, CU]): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[CT, CU]]
    val newBehavior = behavior.withOnError(_onError)
    updateTask(name, newBehavior)

  override def withOnComplete(_onComplete: TaskContext[CT, CU] ?=> Task[CT, CU]): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[CT, CU]]
    val newBehavior = behavior.withOnComplete(_onComplete)
    updateTask(name, newBehavior)

  override def withOnAtomComplete(_onAtomComplete: TaskContext[CT, CU] ?=> Task[CT, CU]): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[CT, CU]]
    val newBehavior = behavior.withOnAtomComplete(_onAtomComplete)
    updateTask(name, newBehavior)

  override def withWrapper(
      _onNext: TaskContext[CT, CU] ?=> (TaskContext[CT, CU] ?=> CT => Task[CT, CU]) => CT => Unit
  ): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[CT, CU]]
    val newBehavior = behavior.withWrapper(_onNext)
    updateTask(name, newBehavior)

  override def withStep(task: Task[CT, CU]): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[CT, CU]]
    val newBehavior = behavior.withStep(task)
    updateTask(name, newBehavior)

  override def withLoop(count: Int)(task: Task[CT, CU]): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[CT, CU]]
    val newBehavior = behavior.withLoop(count)(task)
    updateTask(name, newBehavior)

  override def withAndThen[CCU](task: Task[CU, CCU]): FlowBuilder[T, U, CT, CCU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[CT, CU]]
    val newBehavior = behavior.withAndThen(task)
    updateTask(name, newBehavior).asInstanceOf[FlowBuilder[T, U, CT, CCU]]

  override def allWithOnAtomComplete[WT, WU](
      _onAtomComplete: TaskContext[WT, WU] ?=> Task[WT, WU]
  ): FlowBuilder[T, U, CT, CU] =
    fbctx.wbctx.tasks.foreach { case (name, task) =>
      val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[WT, WU]]
      val newBehavior = behavior.withOnAtomComplete(_onAtomComplete)
      updateTask(name, newBehavior)
    }
    this

  override def allWithWrapper[WT, WU](
      _onNext: TaskContext[WT, WU] ?=> (TaskContext[WT, WU] ?=> WT => Task[WT, WU]) => WT => Unit
  ): FlowBuilder[T | WT, U | WU, CT, CU] =
    fbctx.wbctx.tasks.foreach { case (name, task) =>
      // TODO: this is a hack, as the type of the task is not known
      val behavior = fbctx.wbctx.tasks(name).asInstanceOf[Task[WT, WU]]
      val newBehavior = behavior.withWrapper(_onNext)
      updateTask(name, newBehavior)
    }
    this.asInstanceOf[FlowBuilder[T | WT, U | WU, CT, CU]]
end FlowBuilderImpl //
