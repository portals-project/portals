package portals.api.builder

import portals.*
import portals.application.*
import portals.application.task.AskerTaskContext
import portals.application.task.GenericTask
import portals.application.task.MapTaskContext
import portals.application.task.ProcessorTaskContext
import portals.application.task.ReplierTaskContext

private[portals] class FlowBuilderImpl[T, U, CT, CU](using fbctx: FlowBuilderContext[T, U])
    extends FlowBuilder[T, U, CT, CU]:

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

  private def updateTask(name: String, newBehavior: GenericTask[_, _, _, _]): FlowBuilder[T, U, CT, CU] =
    fbctx.wbctx.tasks = fbctx.wbctx.tasks - name
    fbctx.wbctx.tasks = fbctx.wbctx.tasks + (name -> newBehavior)
    this

  // adds connection from latest task to the provided name
  private def addConnectionTo(name: String): Unit =
    fbctx.latest match
      case Some(n) =>
        fbctx.wbctx.connections = (n, name) :: fbctx.wbctx.connections
      case None => ???

  private def addTaskFrom[CU, CCU](
      behavior: GenericTask[CU, CCU, _, _],
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

  private def addTask[CCU](behavior: GenericTask[CU, CCU, _, _]): FlowBuilder[T, U, CU, CCU] =
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

  override def union(others: List[FlowBuilder[T, U, _, CU]]): FlowBuilder[T, U, CU, CU] =
    val behavior = TaskBuilder.identity[CU]
    addTaskFrom(behavior, this :: others)

  override def from[CU, CCU](others: FlowBuilder[T, U, _, CU]*)(
      task: GenericTask[CU, CCU, _, _]
  ): FlowBuilder[T, U, CU, CCU] =
    addTaskFrom[CU, CCU](task, others.toList)

  override def map[CCU](f: MapTaskContext[CU, CCU] ?=> CU => CCU): FlowBuilder[T, U, CU, CCU] =
    val behavior = TaskBuilder.map[CU, CCU](f)
    addTask(behavior)

  override def key(f: CU => Long): FlowBuilder[T, U, CU, CU] =
    val behavior = TaskBuilder.key[CU](f)
    addTask(behavior)

  override def task[CCU](taskBehavior: GenericTask[CU, CCU, _, _]): FlowBuilder[T, U, CU, CCU] =
    val behavior = taskBehavior
    addTask(behavior)

  override def processor[CCU](f: ProcessorTaskContext[CU, CCU] ?=> CU => Unit): FlowBuilder[T, U, CU, CCU] =
    val behavior = TaskBuilder.processor[CU, CCU](f)
    addTask(behavior)

  override def flatMap[CCU](f: MapTaskContext[CU, CCU] ?=> CU => Seq[CCU]): FlowBuilder[T, U, CU, CCU] =
    val behavior = TaskBuilder.flatMap[CU, CCU](f)
    addTask(behavior)

  override def filter(p: CU => Boolean): FlowBuilder[T, U, CU, CU] =
    val behavior = TaskBuilder.filter[CU](p)
    addTask(behavior)

  override def vsm[CCU](defaultTask: VSMTask[CU, CCU]): FlowBuilder[T, U, CU, CCU] =
    val behavior = TaskBuilder.vsm(defaultTask)
    addTask(behavior)

  override def init[CCU](
      initFactory: ProcessorTaskContext[CU, CCU] ?=> GenericTask[CU, CCU, Nothing, Nothing]
  ): FlowBuilder[T, U, CU, CCU] =
    val behavior = TaskBuilder.init[CU, CCU](initFactory)
    addTask(behavior)

  override def identity(): FlowBuilder[T, U, CU, CU] =
    val behavior = TaskBuilder.identity[CU]
    addTask(behavior)

  override def logger(prefix: String = ""): FlowBuilder[T, U, CU, CU] =
    val behavior = TaskBuilder.logger[CU](prefix)
    addTask(behavior)

  override def checkExpectedType[CCU >: CU <: CU](): FlowBuilder[T, U, CT, CU] = this

  override def withName(name: String): FlowBuilder[T, U, CT, CU] =
    val oldName = fbctx.latest.get
    rename(oldName, name)

  override def withOnNext(_onNext: ProcessorTaskContext[CT, CU] ?=> CT => Unit): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[GenericTask[CT, CU, Nothing, Nothing]]
    val newBehavior = behavior.withOnNext(_onNext)
    updateTask(name, newBehavior)

  override def withOnError(_onError: ProcessorTaskContext[CT, CU] ?=> Throwable => Unit): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[GenericTask[CT, CU, Nothing, Nothing]]
    val newBehavior = behavior.withOnError(_onError)
    updateTask(name, newBehavior)

  override def withOnComplete(_onComplete: ProcessorTaskContext[CT, CU] ?=> Unit): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[GenericTask[CT, CU, Nothing, Nothing]]
    val newBehavior = behavior.withOnComplete(_onComplete)
    updateTask(name, newBehavior)

  override def withOnAtomComplete(_onAtomComplete: ProcessorTaskContext[CT, CU] ?=> Unit): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[GenericTask[CT, CU, Nothing, Nothing]]
    val newBehavior = behavior.withOnAtomComplete(_onAtomComplete)
    updateTask(name, newBehavior)

  override def withWrapper(
      _onNext: ProcessorTaskContext[CT, CU] ?=> (ProcessorTaskContext[CT, CU] ?=> CT => Unit) => CT => Unit
  ): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[GenericTask[CT, CU, Nothing, Nothing]]
    val newBehavior = behavior.withWrapper(_onNext)
    updateTask(name, newBehavior)

  override def withStep(task: GenericTask[CT, CU, Nothing, Nothing]): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[GenericTask[CT, CU, Nothing, Nothing]]
    val newBehavior = behavior.withStep(task)
    updateTask(name, newBehavior)

  override def withLoop(count: Int)(task: GenericTask[CT, CU, Nothing, Nothing]): FlowBuilder[T, U, CT, CU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[GenericTask[CT, CU, Nothing, Nothing]]
    val newBehavior = behavior.withLoop(count)(task)
    updateTask(name, newBehavior)

  override def withAndThen[CCU](task: GenericTask[CU, CCU, Nothing, Nothing]): FlowBuilder[T, U, CT, CCU] =
    val name = fbctx.latest.get
    val behavior = fbctx.wbctx.tasks(name).asInstanceOf[GenericTask[CT, CU, Nothing, Nothing]]
    val newBehavior = behavior.withAndThen(task)
    updateTask(name, newBehavior).asInstanceOf[FlowBuilder[T, U, CT, CCU]]

  override def allWithOnAtomComplete[WT, WU](
      _onAtomComplete: ProcessorTaskContext[WT, WU] ?=> Unit
  ): FlowBuilder[T, U, CT, CU] =
    fbctx.wbctx.tasks.foreach { case (name, task) =>
      val behavior = fbctx.wbctx.tasks(name).asInstanceOf[GenericTask[WT, WU, Nothing, Nothing]]
      val newBehavior = behavior.withOnAtomComplete(_onAtomComplete)
      updateTask(name, newBehavior)
    }
    this

  override def allWithWrapper[WT, WU](
      _onNext: ProcessorTaskContext[WT, WU] ?=> (ProcessorTaskContext[WT, WU] ?=> WT => Unit) => WT => Unit
  ): FlowBuilder[T | WT, U | WU, CT, CU] =
    fbctx.wbctx.tasks.foreach { case (name, task) =>
      val behavior = fbctx.wbctx.tasks(name).asInstanceOf[GenericTask[WT, WU, Nothing, Nothing]]
      val newBehavior = behavior.withWrapper(_onNext)
      updateTask(name, newBehavior)
    }
    this.asInstanceOf[FlowBuilder[T | WT, U | WU, CT, CU]]

  //////////////////////////////////////////////////////////////////////////////
  // Portals
  //////////////////////////////////////////////////////////////////////////////

  override def asker[CCU, Req, Rep](portals: AtomicPortalRefKind[Req, Rep]*)(
      f: AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit
  ): FlowBuilder[T, U, CU, CCU] =
    val behavior = TaskBuilder.portal[Req, Rep](portals: _*).asker[CU, CCU](f)
    addTask(behavior)

  override def replier[CCU, Req, Rep](portals: AtomicPortalRefKind[Req, Rep]*)(
      f1: ProcessorTaskContext[CU, CCU] ?=> CU => Unit
  )(f2: ReplierTaskContext[CU, CCU, Req, Rep] ?=> Req => Unit): FlowBuilder[T, U, CU, CCU] =
    val behavior = TaskBuilder.portal[Req, Rep](portals: _*).replier[CU, CCU](f1)(f2)
    addTask(behavior)

end FlowBuilderImpl // class
