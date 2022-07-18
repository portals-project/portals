package portals

trait WorkflowBuilder[T, U]:
  def build(): Workflow[T, U]

  def source[TT >: T <: T](name: String = null): FlowBuilder[T, U]

  // check if workflow is well-formed
  def check(): Boolean
end WorkflowBuilder // trait

object WorkflowBuilder:
  def apply[T, U](_name: String)(using BuilderContext): WorkflowBuilder[T, U] =
    given wbctx: WorkflowBuilderContext[T, U] = new WorkflowBuilderContext[T, U] { val name = _name }
    new WorkflowBuilderImpl[T, U]
end WorkflowBuilder // object
