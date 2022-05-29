package pods.workflows

private[pods] class WorkflowBuilderImpl(name: String) extends WorkflowBuilder:
  override def build(): Workflow =
    new Workflow(name, this.tasks.toList, this.connections)

  override def source[T](): FlowBuilder[Nothing, T] =
    new FlowBuilderImpl[Nothing, T](this).source()

  override def from[I, O](fb: FlowBuilder[I, O]): FlowBuilder[Nothing, O] =
    new FlowBuilderImpl[Nothing, O](this).from(fb)

  override def merge[I1, I2, O](fb1: FlowBuilder[I1, O], fb2: FlowBuilder[I2, O]): FlowBuilder[Nothing, O] =
    new FlowBuilderImpl[Nothing, O](this).merge(fb1, fb2)

  override def cycle[T](): FlowBuilder[T, T] =
    new FlowBuilderImpl[T, T](this).cycle()

end WorkflowBuilderImpl