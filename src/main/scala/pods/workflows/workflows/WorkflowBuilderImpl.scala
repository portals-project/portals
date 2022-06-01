package pods.workflows

private[pods] class WorkflowBuilderImpl(name: String) extends WorkflowBuilder:
  override def build(): Workflow =
    new Workflow(name, this.tasks.toList, this.connections)

  override def source[T](): AtomicStream[Nothing, T] =
    new AtomicStreamImpl[Nothing, T](this).source()

  override def from[I, O](fb: AtomicStream[I, O]): AtomicStream[Nothing, O] =
    new AtomicStreamImpl[Nothing, O](this).from(fb)

  override def merge[I1, I2, O](fb1: AtomicStream[I1, O], fb2: AtomicStream[I2, O]): AtomicStream[Nothing, O] =
    new AtomicStreamImpl[Nothing, O](this).merge(fb1, fb2)

  override def cycle[T](): AtomicStream[T, T] =
    new AtomicStreamImpl[T, T](this).cycle()

end WorkflowBuilderImpl