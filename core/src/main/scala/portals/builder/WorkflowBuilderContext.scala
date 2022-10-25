package portals

import scala.compiletime.ops.boolean

class WorkflowBuilderContext[T, U](_path: String, _name: String)(using val bctx: ApplicationBuilderContext):
  val path: String = _path

  var tasks: Map[String, Task[_, _]] = Map.empty
  var connections: List[(String, String)] = List.empty
  var sources: Map[String, Task[T, _]] = Map.empty
  var sinks: Map[String, Task[_, U]] = Map.empty

  private val _stream: AtomicStream[U] = AtomicStream[U](path + "/" + "stream")

  val streamref: AtomicStreamRef[U] = AtomicStreamRef[U](_stream)

  var consumes: AtomicStreamRefKind[T] = null

  private var frozen: Boolean = false

  def freeze(): Workflow[T, U] =
    if frozen == true then ???
    else
      frozen = true
      val wf = new Workflow[T, U](
        path = path,
        consumes = consumes,
        stream = streamref,
        tasks = tasks,
        sources = sources,
        sinks = sinks,
        connections = connections
      )
      bctx.addToContext(wf)
      bctx.addToContext(_stream)
      wf
