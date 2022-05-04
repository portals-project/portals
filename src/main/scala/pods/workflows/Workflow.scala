package pods.workflows

class Workflow[I, O](
    val name: String,
    val tasks: Map[String, Task[I, O]],
    val connections: List[(String, String)]
)

object Workflows {
  def apply(name: String): WorkflowBuilder[_, _] =
    new WorkflowBuilder[Nothing, Nothing](name)

  def build(name: String): WorkflowBuilder[_, _] =
    new WorkflowBuilder[Nothing, Nothing](name)
}

class WorkflowBuilder[I, O](val name: String):
  var tasks: Map[String, TaskBehavior[_, _]] =
    Map.empty // includes sinks / sources
  var connections: List[(String, String)] = List.empty
  var latest: Option[String] = None

  private def nameFromName(name: String): String =
    name match
      case s if s == "" => this.name + "$" + tasks.size
      case s            => this.name + "$" + s // + "$" + tasks.size

  def source[T](name: String): WorkflowBuilder[Nothing, T] =
    val behavior = TaskBehaviors.identity[T]
    val taskName = nameFromName(name)
    tasks = tasks + (taskName -> behavior)
    latest = Some(taskName)
    this.asInstanceOf[WorkflowBuilder[Nothing, T]]

  def sink[T](name: String): WorkflowBuilder[T, Nothing] =
    val behavior = TaskBehaviors.identity[T]
    val taskName = nameFromName(name)
    tasks = tasks + (taskName -> behavior)
    connections = connections :+ (latest.get, taskName)
    latest = None
    this.asInstanceOf[WorkflowBuilder[T, Nothing]]

  def processor[T](
      behavior: TaskBehavior[O, T],
      name: String
  ): WorkflowBuilder[I, T] =
    val taskName = nameFromName(name)
    tasks = tasks + (taskName -> behavior)
    connections = connections :+ (latest.get, taskName)
    latest = Some(taskName)
    this.asInstanceOf[WorkflowBuilder[I, T]]

  def map[T](f: O => T, name: String): WorkflowBuilder[I, T] =
    val behavior = TaskBehaviors.map[O, T](f)
    val taskName = nameFromName(name)
    tasks = tasks + (taskName -> behavior)
    connections = connections :+ (latest.get, taskName)
    latest = Some(taskName)
    this.asInstanceOf[WorkflowBuilder[I, T]]

  def flatMap[T](
      f: O => TraversableOnce[T],
      name: String
  ): WorkflowBuilder[I, T] =
    val behavior = TaskBehaviors.flatMap[O, T](f)
    val taskName = nameFromName(name)
    tasks = tasks + (taskName -> behavior)
    connections = connections :+ (latest.get, taskName)
    latest = Some(taskName)
    this.asInstanceOf[WorkflowBuilder[I, T]]

  def build(): Workflow[I, O] =
    val ts = tasks.map({ case (name, behavior) =>
      (name, Tasks(behavior))
    })
    val cs = connections.map({ case (from, to) =>
      Tasks.connect[Any](ts(from).asInstanceOf, ts(to).asInstanceOf)
      (from, to)
    })
    new Workflow(name, ts.asInstanceOf, cs)

@main def testWorkflow() =
  val wf = Workflows
    .build("wf")
    .source[Int]("source")
    .map(x => { println(x); x * 2 }, "map")
    .map(_ * 2, "map2")
    .sink[Int]("sink")
    .build()

  wf.tasks("wf$source").tctx.ic.worker.submit(1)
  wf.tasks("wf$source").tctx.ic.worker.submit(1)
  Thread.sleep(100)
