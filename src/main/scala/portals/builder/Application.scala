package portals

class Application(
    // private[portals] val path: String,
    private[portals] val name: String,
    private[portals] val workflows: List[Workflow[_, _]]
):
  override def toString(): String = toString(0)

  def toString(indent: Int): String =
    def space(indent: Int) = " " * indent
    val sb = new StringBuilder("")
    // application
    sb ++= space(indent) + s"Application: $name\n"
    // workflows
    sb ++= space(indent + 1) + s"workflows: \n"
    sb ++= s"${workflows.map(_.toString(indent + 2)).mkString("\n")}"
    sb.toString()

end Application // class
