package portals

import scala.annotation.targetName

private[portals] class WorkflowBuilderImpl[T, U](using wbctx: WorkflowBuilderContext[T, U])
    extends WorkflowBuilder[T, U]:

  // internal, do not use
  // todo: solve this in some better way :)
  private[portals] override def complete(): Unit =
    if wbctx.frozen == true then () else wbctx.freeze()

  override def freeze(): Workflow[T, U] = wbctx.freeze()
  override def source[TT >: T <: T](name: String = null): FlowBuilder[T, U] =
    FlowBuilder(None).source(name)

  @targetName("sourceFromRef")
  override def source[TT >: T <: T](ref: AtomicStreamRef[T]): FlowBuilder[T, U] =
    FlowBuilder(None).source(ref)

  override def check(): Boolean = ???
end WorkflowBuilderImpl
