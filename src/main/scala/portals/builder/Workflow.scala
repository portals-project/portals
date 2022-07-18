package portals

class Workflow[T, U](
    // names
    // private[portals] val path: String,
    private[portals] val name: String,
    // workflow internals
    private[portals] val tasks: Map[String, Task[_, _]],
    private[portals] val sources: Map[String, Task[T, _]],
    private[portals] val sinks: Map[String, Task[_, U]],
    private[portals] val connections: List[(String, String)]
    // workflow stream and sequencer
    // private[portals] val stream: AtomicStream[U],
    // private[portals] val sequencer: Option[AtomicSequencer[T]]
):
  override def toString(): String = toString(0)

  def toString(indent: Int): String =
    def space(indent: Int) = " " * indent
    val sb = new StringBuilder("")
    // workflow
    sb ++= space(indent) + s"Workflow(\n"
    sb ++= space(indent + 1) + s"name: $name,\n"
    sb ++= space(indent + 1) + s"sources: $sources,\n"
    sb ++= space(indent + 1) + s"tasks: $tasks,\n"
    sb ++= space(indent + 1) + s"sinks: $sinks,\n"
    sb ++= space(indent + 1) + s"connections: $connections,\n"
    sb ++= space(indent) + s")\n"
    sb.toString()
end Workflow // class
