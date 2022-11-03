package portals

import scala.annotation.targetName

private[portals] class WorkflowBuilderImpl[T, U](using wbctx: WorkflowBuilderContext[T, U])
    extends WorkflowBuilder[T, U]:

  // internal, do not use
  private[portals] override def complete(): Unit =
    try wbctx.freeze()
    // FIXME: we should not ignore the error here, it happens when we call freeze twice
    catch e => ()

  override def freeze(): Workflow[T, U] = wbctx.freeze()

  override def source[TT >: T <: T](ref: AtomicStreamRefKind[T]): FlowBuilder[T, U, TT, TT] =
    FlowBuilder(None).source(ref)

  override def check(): Boolean = ???
end WorkflowBuilderImpl
