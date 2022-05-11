package pods.workflows

class Workflow(
    val tasks: Map[String, Task[_, _]],
    val connections: List[(String, String)]
)

object Workflows {
  def apply(): WorkflowBuilder =
    new WorkflowBuilder()

  def builder(): WorkflowBuilder =
    new WorkflowBuilder()

  def connect(t1: Task[_, _], t2: Task[_, _]): Unit =
    Tasks.connect(t1.asInstanceOf, t2.asInstanceOf)
}

class WorkflowBuilder:
  var tasks: Map[String, TaskBehavior[_, _]] = Map.empty
  var connections: List[(String, String)] = List.empty

  def source[T](name: String): FlowBuilder[Nothing, T] =
    new FlowBuilder(this).source[T](name)

  def from[I, O](flow: FlowBuilder[I, O]): FlowBuilder[Nothing, O] =
    new FlowBuilder(this).from(flow)

  def merge[I1, I2, O](
      flow1: FlowBuilder[I1, O],
      flow2: FlowBuilder[I2, O]
  ): FlowBuilder[Nothing, O] =
    new FlowBuilder(this).merge(flow1, flow2)

  def cycle[T](): FlowBuilder[T, T] =
    new FlowBuilder(this).cycle()

  def build(): Workflow =
    val ts = tasks.map({ case (name, behavior) =>
      (name, Tasks(behavior))
    })
    val cs = connections.map({ case (from, to) =>
      Tasks.connect[Any](ts(from).asInstanceOf, ts(to).asInstanceOf)
      (from, to)
    })
    new Workflow(ts.asInstanceOf, cs).asInstanceOf[Workflow]

class FlowBuilder[I, O](workflow: WorkflowBuilder):
  var cycleIn: Option[String] = None
  var latest: Option[String] = None

  private def nameFromName(name: String): String =
    name match
      case s if s == "" => "$" + workflow.tasks.size
      case s            => s // + "$" + workflow.tasks.size

  def withOnNext(
      _onNext: TaskContext[I, O] ?=> I => TaskBehavior[I, O]
  ): FlowBuilder[I, O] =
    latest match
      case Some(name) =>
        val behavior = workflow.tasks(name)
        val newBehavior = new TaskBehavior[I, O] {
          override def onNext(tctx: TaskContext[I, O], t: I): TaskBehavior[I, O] =
            _onNext(using tctx)(t)
          override def onError(tctx: TaskContext[I, O], t: Throwable): TaskBehavior[I, O] =
            behavior.onError(tctx.asInstanceOf, t).asInstanceOf
          override def onComplete(tctx: TaskContext[I, O]): TaskBehavior[I, O] =
            behavior.onComplete(tctx.asInstanceOf).asInstanceOf
        }
        workflow.tasks += (name -> newBehavior)
        this
      case None => ??? // bad

  private[pods] def cycle(): FlowBuilder[I, O] =
    val behavior = TaskBehaviors.identity[O]
    val taskName = nameFromName("")
    cycleIn = Some(taskName)
    latest = Some(taskName)
    workflow.tasks = workflow.tasks + (taskName -> behavior)
    this

  def intoCycle(flow: FlowBuilder[O, O]): FlowBuilder[I, Nothing] =
    flow.cycleIn match
      case Some(to) =>
        latest match
          case Some(from) =>
            workflow.connections = workflow.connections :+ (from, to)
            this.asInstanceOf[FlowBuilder[I, Nothing]]
          case None => ??? // bad
      case None => ??? // bad

  def from(flow: FlowBuilder[I, O]): FlowBuilder[Nothing, O] =
    latest = flow.latest
    this.asInstanceOf[FlowBuilder[Nothing, O]]

  def merge[I1, I2, O](
      flow1: FlowBuilder[I1, O],
      flow2: FlowBuilder[I2, O]
  ): FlowBuilder[Nothing, O] =
    val behavior = TaskBehaviors.identity[O]
    val taskName = nameFromName("")
    workflow.tasks = workflow.tasks + (taskName -> behavior)
    workflow.connections = workflow.connections ++ List(
      (flow1.latest.get, taskName),
      (flow2.latest.get, taskName)
    )
    latest = Some(taskName)
    this.asInstanceOf[FlowBuilder[Nothing, O]]

  def source[T](name: String): FlowBuilder[Nothing, T] =
    val behavior = TaskBehaviors.identity[T]
    val taskName = nameFromName(name)
    workflow.tasks = workflow.tasks + (taskName -> behavior)
    latest = Some(taskName)
    this.asInstanceOf[FlowBuilder[Nothing, T]]

  def sink[T](name: String): FlowBuilder[T, Nothing] =
    val behavior = TaskBehaviors.identity[T]
    val taskName = nameFromName(name)
    workflow.tasks = workflow.tasks + (taskName -> behavior)
    workflow.connections = workflow.connections :+ (latest.get, taskName)
    latest = None
    this.asInstanceOf[FlowBuilder[T, Nothing]]

  def processor[T](
      f: TaskContext[O, T] ?=> O => TaskBehavior[O, T],
      name: String = ""
  ): FlowBuilder[O, T] =
    val behavior = TaskBehaviors.processor(f)
    val taskName = nameFromName(name)
    workflow.tasks = workflow.tasks + (taskName -> behavior)
    workflow.connections = workflow.connections :+ (latest.get, taskName)
    latest = Some(taskName)
    this.asInstanceOf[FlowBuilder[O, T]]

  def map[OO, T](f: O => T, name: String = ""): FlowBuilder[I, T] =
    val behavior = TaskBehaviors.map[O, T](f)
    val taskName = nameFromName(name)
    workflow.tasks = workflow.tasks + (taskName -> behavior)
    workflow.connections = workflow.connections :+ (latest.get, taskName)
    latest = Some(taskName)
    this.asInstanceOf[FlowBuilder[I, T]]

  def flatMap[OO, T](
      f: O => TraversableOnce[T],
      name: String = ""
  ): FlowBuilder[I, T] =
    val behavior = TaskBehaviors.flatMap[O, T](f)
    val taskName = nameFromName(name)
    workflow.tasks = workflow.tasks + (taskName -> behavior)
    workflow.connections = workflow.connections :+ (latest.get, taskName)
    latest = Some(taskName)
    this.asInstanceOf[FlowBuilder[I, T]]

  def build(): Workflow =
    workflow.build()

@main def testExternalCycle() =
  val wf1 = Workflows
    .builder()
    .source[Int]("source")
    .processor({ tctx ?=> x =>
      tctx.log.info(x.toString)
      tctx.emit(x)
      TaskBehaviors.same
    })
    .sink[Int]("sink")
    .build()

  val wf2 = Workflows
    .builder()
    .source[Int]("source2")
    .processor[Int]({ tctx ?=> x =>
      tctx.log.info(x.toString)
      tctx.emit(x)
      TaskBehaviors.same
    })
    .sink[Int]("sink2")
    .build()

  // let's create a cycle between wf1 and wf2
  Workflows.connect(wf2.tasks("sink2"), wf1.tasks("source"))
  Workflows.connect(wf1.tasks("sink"), wf2.tasks("source2"))

  // this should now cause infinite loop, printing messages
  wf2.tasks("source2").tctx.ic.worker.submit(1.asInstanceOf)

  Thread.sleep(100)

@main def testDynamicCall() =
  val wf1 = Workflows
    .builder()
    .source[Int]("source")
    .processor({ tctx ?=> x =>
      tctx.log.info(x.toString)
      tctx.emit(x)
      TaskBehaviors.same
    })
    .sink[Int]("sink")
    .build()

  val wf2 = Workflows
    .builder()
    .source[Int]("source2")
    .processor[Int]({ tctx ?=> x =>
      tctx.log.info(x.toString)
      // dynamic emit :), creates connection to other workflow
      tctx.emit(wf1.tasks("source").tctx.ic, x)
      TaskBehaviors.same
    })
    .sink[Int]("sink2")
    .build()

  // this should result in two messages printed, one for each workflow
  wf2.tasks("source2").tctx.ic.worker.submit(1.asInstanceOf)

  Thread.sleep(100)

@main def testWorkflow() =
  // here we try out various DSL features
  val builder = Workflows.builder()

  val flow = builder
    .source[Int]("source")
    .map[Int, Int](_ * 2)
    .processor({ tctx ?=> x =>
      tctx.log.info("processor: " + x)
      tctx.emit(x)
      TaskBehaviors.same
    })
    .withOnNext({ tctx ?=> x =>
      // override the OnNext method, also works for other behavior methods
      tctx.emit(x)
      tctx.log.info("onNextOverridden: " + x)
      TaskBehaviors.same
    })
    .flatMap[Int, String](x => List(x.toString))

  val split1 = builder.from(flow).map(identity)
  val split2 = builder.from(flow).map(identity)

  val merged: FlowBuilder[Nothing, String] = builder.merge(split1, split2)

  val cycle = builder.cycle[String]()

  val mergedWithCycle =
    builder
      .merge(cycle, merged)
      .map(identity)
      .processor({ tctx ?=> x =>
        tctx.log.info("in cycle: " + x)
        tctx.emit(x)
        TaskBehaviors.same
      })
      .intoCycle(cycle.asInstanceOf)

  val sink = builder
    .from(mergedWithCycle)
    .sink[String]("sink")

  val wf = builder.build()

  wf.tasks("source").tctx.ic.worker.submit(1.asInstanceOf)

  Thread.sleep(100)
