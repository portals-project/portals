package portals

/** Application Builder Context. */
trait BuilderContext:
  val name: String
  var workflows: List[WorkflowBuilder[_, _]] = List.empty
