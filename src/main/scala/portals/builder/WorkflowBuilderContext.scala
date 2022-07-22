package portals

import scala.compiletime.ops.boolean

class WorkflowBuilderContext[T, U](_path: String, _name: String)(using val bctx: ApplicationBuilderContext):
  val path: String = _path
  val name: String = _name

  var tasks: Map[String, Task[_, _]] = Map.empty
  var connections: List[(String, String)] = List.empty
  var sources: Map[String, Task[T, _]] = Map.empty
  var sinks: Map[String, Task[_, U]] = Map.empty

  private val _stream: AtomicStream[U] = AtomicStream[U](
    name = "stream",
    path = path + "/" + "stream"
  )
  val stream: AtomicStreamRef[U] = AtomicStreamRef[U](
    name = "stream",
    path = _path + "/" + "stream",
  )

  private[portals] var consumes: AtomicStreamRef[T] = null
  var sequencer: Option[AtomicSequencerRef[T]] = None

  var _next_task_id: Int = 0
  def next_task_id(): String =
    _next_task_id = _next_task_id + 1
    "$" + _next_task_id.toString

  var frozen: Boolean = false

  def freeze(): Workflow[T, U] =
    if frozen == true then ???
    else
      frozen = true
      val wf = new Workflow[T, U](
        path = path,
        name = name,
        consumes = consumes,
        stream = stream,
        sequencer = sequencer,
        tasks = tasks,
        sources = sources,
        sinks = sinks,
        connections = connections
      )
      bctx.addToContext(wf)
      bctx.addToContext(_stream)
      wf
