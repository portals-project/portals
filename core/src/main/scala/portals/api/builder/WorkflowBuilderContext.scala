package portals

import scala.compiletime.ops.boolean

class WorkflowBuilderContext[T, U](_path: String, _name: String)(using val bctx: ApplicationBuilderContext):
  self =>
  val path: String = _path

  var tasks: Map[String, GenericTask[_, _, _, _]] = Map.empty
  var source: Option[String] = None
  var sink: Option[String] = None
  var connections: List[(String, String)] = List.empty

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
        tasks = self.tasks,
        source = source.get,
        sink = sink.get,
        connections = connections
      )
      bctx.addToContext(wf)
      bctx.addToContext(_stream)
      wf
