package portals.api.builder

import portals.*

private[portals] class WorkflowBuilderImpl[T, U](using wbctx: WorkflowBuilderContext[T, U])
    extends WorkflowBuilder[T, U]:

  // internal, do not use, completes anything that wasn't frozen
  private[portals] override def complete(): Unit =
    try wbctx.freeze()
    catch e => ()

  override def freeze(): Workflow[T, U] = wbctx.freeze()

  override def source[TT >: T <: T](ref: AtomicStreamRefKind[T]): FlowBuilder[T, U, TT, TT] =
    FlowBuilder(None).source(ref)
end WorkflowBuilderImpl
