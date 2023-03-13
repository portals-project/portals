package portals.api.builder

import portals.*
import portals.application.*

/** Builder for workflows.
  *
  * Accessed from the application builder via `builder.workflows`.
  *
  * @example
  *   {{{builder.workflows[String, Int]("workflowName").source().map(x => x.length()).sink().freeze()}}}
  */
trait WorkflowBuilder[T, U]:
  /** Internal API. Freezes and completes a workflow that was not frozen by the
    * user. Called by the system when the application is built.
    */
  private[portals] def complete(): Unit

  /** Freeze a workflow, this will prevent any further changes to the workflow.
    *
    * In order to use a workflow's output further in the application, it must be
    * frozen so that its output stream can be accessed.
    *
    * @return
    *   reference to the workflow
    */
  def freeze(): Workflow[T, U]

  /** Start the workflow from the source stream `ref`. Returns a flow.
    *
    * The additional type parameter is used to make manual checks that the type
    * is correct, i.e. `source[Int](ref)` ensures that the input type is Int.
    *
    * @example
    *   {{{builder.workflows[String, Int]("workflowName").source(stream).map(x => x.length())}}}
    *
    * @tparam TT
    *   type of the source stream
    * @param ref
    *   reference to the source stream
    * @return
    *   a flow starting from the source stream
    */
  def source[TT >: T <: T](ref: AtomicStreamRefKind[T]): FlowBuilder[T, U, TT, TT]
end WorkflowBuilder // trait

/** Internal API. The workflow builder. */
object WorkflowBuilder:
  /** Internal API. Create a WorkflowBuilder using the application context. */
  def apply[T, U](name: String)(using bctx: ApplicationBuilderContext): WorkflowBuilder[T, U] =
    val _path = bctx.app.path + "/workflows/" + name
    given wbctx: WorkflowBuilderContext[T, U] = new WorkflowBuilderContext[T, U](_path = _path)
    new WorkflowBuilderImpl[T, U]()
end WorkflowBuilder // object
