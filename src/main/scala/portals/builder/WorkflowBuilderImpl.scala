package portals

private[portals] class WorkflowBuilderImpl[T, U](using wbctx: WorkflowBuilderContext[T, U])
    extends WorkflowBuilder[T, U]:
  override def build(): Workflow[T, U] =
    new Workflow[T, U](wbctx.name, wbctx.tasks, wbctx.sources, wbctx.sinks, wbctx.connections)

  override def source[TT >: T <: T](name: String = null): FlowBuilder[T, U] =
    FlowBuilder(None).source(name)

  override def check(): Boolean = ???
end WorkflowBuilderImpl
