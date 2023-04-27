package portals.api.builder

import scala.annotation.experimental
import scala.annotation.targetName

import portals.api.builder.TaskExtensions.*
import portals.application.*
import portals.application.task.AskerReplierTaskContext
import portals.application.task.AskerTaskContext
import portals.application.task.GenericTask
import portals.application.task.MapTaskContext
import portals.application.task.ProcessorTaskContext
import portals.application.task.ReplierTaskContext

/** Flow Builder
  *
  * @tparam T
  *   the input type of the flow
  * @tparam U
  *   the output type of the flow
  * @tparam CT
  *   the current input type of the latest task
  * @tparam CU
  *   the current output type of the latest task
  */
trait FlowBuilder[T, U, CT, CU]:
  //////////////////////////////////////////////////////////////////////////////
  // Freezing
  //////////////////////////////////////////////////////////////////////////////

  /** Freeze the flow builder, returns the frozen workflow.
    *
    * @note
    *   The workflow cannot be modified once frozen.
    * @return
    *   the frozen workflow
    */
  def freeze(): Workflow[T, U]

  //////////////////////////////////////////////////////////////////////////////
  // Sources and sinks
  //////////////////////////////////////////////////////////////////////////////

  private[portals] def source[CC >: T <: T](ref: AtomicStreamRefKind[T]): FlowBuilder[T, U, CC, CC]

  /** End the flow in the sink.
    *
    * @note
    *   The output type of the current flowbuilder shas to have the same type as
    *   the sink / workflow output.
    * @return
    *   a new flow builder
    */
  def sink[CC >: CU | U <: CU & U](): FlowBuilder[T, U, U, U]

  //////////////////////////////////////////////////////////////////////////////
  // Structural operations
  //////////////////////////////////////////////////////////////////////////////

  /** Union the flow of the current flow with the `others` flows.
    *
    * @param others
    *   list of other flows which will be unioned with the current flow
    * @return
    *   a new flow builder
    */
  def union(others: List[FlowBuilder[T, U, _, CU]]): FlowBuilder[T, U, CU, CU]

  /** Union the flow of the current flow with the `others` flows.
    *
    * @param others
    *   the other flows which will be unioned with the current flow
    * @return
    *   a new flow builder
    */
  def union(others: FlowBuilder[T, U, _, CU]*): FlowBuilder[T, U, CU, CU] = union(others.toList)

  /** Combine and union all provided `flows` and process by `task`. Note, this
    * does not union with the current flow.
    *
    * TODO: this should not be here, as it is confusing that it does not union
    * with the current flow.
    *
    * @param task
    *   the task to process the combined flows
    * @param flows
    *   the flows which will be unioned (NOT unioned with current flow)
    * @return
    *   a new flow builder
    */
  def combineAllFrom[CU, CCU](flows: FlowBuilder[T, U, _, CU]*)(
      task: GenericTask[CU, CCU, _, _]
  ): FlowBuilder[T, U, CU, CCU] =
    flows.head.union(flows.tail.toList).task(task)

  /** Split a flow into two disjoint flows by the provided predicates `p1` and
    * `p2`.
    *
    * @param p1
    *   the first splitting predicate
    * @param p2
    *   the second splitting predicate
    * @return
    *   two flow builders, one for each split
    */
  def split(
      p1: PartialFunction[CU, Boolean],
      p2: PartialFunction[CU, Boolean]
  ): (FlowBuilder[T, U, CU, CU], FlowBuilder[T, U, CU, CU]) =
    (this.filter { x => p1.apply(x) }, this.filter { x => p2.apply(x) })

  //////////////////////////////////////////////////////////////////////////////
  // Stateful transformations
  //////////////////////////////////////////////////////////////////////////////

  /** Map the current flow with the provided function `f`.
    *
    * @tparam CCU
    *   the new output type of the mapped flow
    * @param f
    *   the function to map the current flow
    * @return
    *   the map flow
    */
  def map[CCU](f: MapTaskContext[CU, CCU] ?=> CU => CCU): FlowBuilder[T, U, CU, CCU]

  /** Compute and shuffle the flow according to a key extracted by `f`.
    *
    * @param f
    *   key extractor function
    * @return
    *   flow shuffled by new key
    */
  def key(f: CU => Long): FlowBuilder[T, U, CU, CU]

  /** Transform the flow by the provided taskBehavior.
    *
    * @tparam CCU
    *   the new output type of the mapped flow
    * @param taskBehavior
    *   the task behavior to transform the flow
    * @return
    *   the transformed flow by the provided task
    */
  def task[CCU](taskBehavior: GenericTask[CU, CCU, _, _]): FlowBuilder[T, U, CU, CCU]

  /** Transform the flow by a provided processor `f`.
    *
    * The processor function `f` may access the `ProcessorTaskContext`, which
    * allows it to `emit` events, access `state`, log messages, etc.
    *
    * @tparam CCU
    *   the new output type of the mapped flow
    * @param f
    *   the processor function
    * @return
    *   the transformed flow by the processor function
    */
  def processor[CCU](f: ProcessorTaskContext[CU, CCU] ?=> CU => Unit): FlowBuilder[T, U, CU, CCU]

  /** Transform the flow by flatMapping by the provided function `f`.
    *
    * Note, you cannot emit events from the provided flatMap function, instead
    * the events produced by the flatMap function will be emitted by the
    * returned flow.
    *
    * @tparam CCU
    *   the new output type of the mapped flow
    * @param f
    *   the function to flatMap the current flow
    * @return
    *   the flatMapped flow
    */
  def flatMap[CCU](f: MapTaskContext[CU, CCU] ?=> CU => Seq[CCU]): FlowBuilder[T, U, CU, CCU]

  /** Use predicate `p` to filter the events of the flow.
    *
    * @param p
    *   the predicate to filter the flow
    * @return
    *   the filtered flow
    */
  def filter(p: CU => Boolean): FlowBuilder[T, U, CU, CU]

  /** Start an instance of a VSM task.
    *
    * Starts an instance of a VSM task. The VSM task is a stateful task behavior
    * which manages the task in a per-key context. To use this you must provide
    * an initial `VSMTask` behavior, which can be created using
    * `VSMTasks.processor`. The VSMTask can still access state, emit events, and
    * more as can the normal tasks.
    *
    * @see
    *   [[portals.api.builder.TaskExtensions.VSMTask]]
    *
    * @example
    *   {{{
    * // VSMTasks
    * val init = VSMExtension.processor { event => started }
    * val started = VSMExtension.processor { event => init }
    * val vsm = VSMExtension.vsm[Int, Int] { init }
    *
    * // flow
    * val flow: FlowBuilder[...] = ...
    * flow.vsm(init)
    *   }}}
    *
    * @param defaultTask
    *   the default task to use as an initial task behavior
    */
  def vsm[CCU](defaultTask: VSMTask[CU, CCU]): FlowBuilder[T, U, CU, CCU]

  /** Provide an initialization factory for a task that transforms this flow.
    *
    * The initialization factory is called at runtime to create the task. This
    * can be used to initial various objects, such as state, to be used by the
    * processing behaviors.
    *
    * Note: this may be **re-executed** more than once, every time that the task
    * is restarted (e.g. after a failure).
    *
    * @tparam CCU
    *   the new output type of the transformed flow
    * @param initFactory
    *   the task initialization factory
    * @return
    *   the transformed flow
    */
  def init[CCU](
      initFactory: ProcessorTaskContext[CU, CCU] ?=> GenericTask[CU, CCU, Nothing, Nothing]
  ): FlowBuilder[T, U, CU, CCU]

  //////////////////////////////////////////////////////////////////////////////
  // Useful operators
  //////////////////////////////////////////////////////////////////////////////

  /** Transform by the identity function (i.e. do nothing).
    *
    * Can be used to create actual transforming behavior using the modifiers
    * such as `withOnNext`.
    *
    * @return
    *   the transformed flow
    */
  def identity(): FlowBuilder[T, U, CU, CU]

  /** Log the current flow with the provided prefix.
    *
    * @param prefix
    *   the prefix for logged messages
    * @return
    *   the logged flow
    */
  def logger(prefix: String = ""): FlowBuilder[T, U, CU, CU]

  /** Check the current type against the provided expected type.
    *
    * Compares FlowBuilder[T, U, CT, CU] with CCU, succeeds if CU <: CCU <: CU.
    *
    * @tparam CCU
    *   the expected type
    */
  def checkExpectedType[CCU >: CU <: CU](): FlowBuilder[T, U, CT, CU]

  //////////////////////////////////////////////////////////////////////////////
  // Combinators
  //////////////////////////////////////////////////////////////////////////////

  /** Give a name to the current task (latest task).
    *
    * The current task is the latest task that was used to transform on this
    * flow.
    *
    * @param name
    *   the new name of the current task
    * @return
    *   the current flow
    */
  def withName(name: String): FlowBuilder[T, U, CT, CU]

  /** Set the onNext handler of the current task to the provided handler.
    *
    * @param onNext
    *   the new onNext handler
    * @return
    *   the current flow
    */
  def withOnNext(onNext: ProcessorTaskContext[CT, CU] ?=> CT => Unit): FlowBuilder[T, U, CT, CU]

  /** Set the onError handler of the current task to the provided handler.
    *
    * @param onError
    *   the new onError handler
    * @return
    *   the current flow
    */
  def withOnError(onError: ProcessorTaskContext[CT, CU] ?=> Throwable => Unit): FlowBuilder[T, U, CT, CU]

  /** Set the onComplete handler of the current task to the provided handler.
    *
    * @param onComplete
    *   the new onComplete handler
    * @return
    *   the current flow
    */
  def withOnComplete(onComplete: ProcessorTaskContext[CT, CU] ?=> Unit): FlowBuilder[T, U, CT, CU]

  /** Set the onAtomComplete handler of the current task to the provided
    * handler.
    *
    * @param onAtomComplete
    *   the new onAtomComplete handler
    * @return
    *   the current flow
    */
  def withOnAtomComplete(onAtomComplete: ProcessorTaskContext[CT, CU] ?=> Unit): FlowBuilder[T, U, CT, CU]

  /** Wrap around the behavior of the current task by the wrapping function `f`.
    *
    * @example
    *   {{{
    * FlowBuilder[_, _, Int, Int]
    *   .withWrapper{ ctx ?=> wrapped => event =>
    *     if event < 3 then ctx.emit(0) else wrapped(event)
    *   }
    *   }}}
    *
    * @param f
    *   the wrapping function
    * @return
    *   the current flow
    */
  def withWrapper(
      f: ProcessorTaskContext[CT, CU] ?=> (ProcessorTaskContext[CT, CU] ?=> CT => Unit) => CT => Unit
  ): FlowBuilder[T, U, CT, CU]

  /** Behavior modifier to step over atoms for current task.
    *
    * This will execute the provided `task` on the subsequent atom.
    *
    * @param task
    *   the task to execute on the subsequent atom
    * @return
    *   the transformed flow
    */
  def withStep(task: GenericTask[CT, CU, Nothing, Nothing]): FlowBuilder[T, U, CT, CU]

  /** Behavior modifier to loop over atoms for current task.
    *
    * This will loop and execute the provided `task` on the `count` next atoms.
    *
    * @param count
    *   the number of times to loop
    * @param task
    *   the task to looped
    * @return
    *   the transformed flow
    */
  def withLoop(count: Int)(task: GenericTask[CT, CU, Nothing, Nothing]): FlowBuilder[T, U, CT, CU]

  /** Chaining `task` after the current task.
    *
    * This is used to explicitly chain tasks as a single task behavior.
    *
    * @param task
    *   the task to chain after the current task
    * @return
    *   the transformed flow
    */
  def withAndThen[CCU](task: GenericTask[CU, CCU, Nothing, Nothing]): FlowBuilder[T, U, CT, CCU]

  //////////////////////////////////////////////////////////////////////////////
  // All* Combinators
  //////////////////////////////////////////////////////////////////////////////

  /** Apply the provided `onAtomComplete` handler to all tasks of the workflow.
    *
    * @param onAtomComplete
    *   the onAtomComplete handler to be applied to all tasks
    * @return
    *   the transformed flow
    */
  def allWithOnAtomComplete[WT, WU](onAtomComplete: ProcessorTaskContext[WT, WU] ?=> Unit): FlowBuilder[T, U, CT, CU]

  /** Apply and wrap the provided wrapper `f` to all tasks of the workflow.
    *
    * @param f
    *   the wrapper to be applied to all tasks
    * @return
    *   the transformed flow
    */
  def allWithWrapper[WT, WU](
      f: ProcessorTaskContext[WT, WU] ?=> (ProcessorTaskContext[WT, WU] ?=> WT => Unit) => WT => Unit
  ): FlowBuilder[T | WT, U | WU, CT, CU]

  //////////////////////////////////////////////////////////////////////////////
  // Portals
  //////////////////////////////////////////////////////////////////////////////

  /** Transform the flow by an asker task, binds to portal `p` and executes the
    * asker handler `f`.
    *
    * @param portals
    *   the portals to bind to
    * @param f
    *   the asker handler
    */
  def asker[CCU, Req, Rep](
      portals: AtomicPortalRefKind[Req, Rep]*
  )(
      f: AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit
  ): FlowBuilder[T, U, CU, CCU]

  /** Transform the flow by a replier task, binds to portal `p`, executes the
    * handler `f1` on regular events, executes the replier handler `f2` on
    * requests.
    *
    * @param portals
    *   the portals to bind to
    * @param f1
    *   the processor handler
    * @param f2
    *   the replier handler
    */
  def replier[CCU, Req, Rep](
      portals: AtomicPortalRefKind[Req, Rep]*
  )(
      f1: ProcessorTaskContext[CU, CCU] ?=> CU => Unit
  )(
      f2: ReplierTaskContext[CU, CCU, Req, Rep] ?=> Req => Unit
  ): FlowBuilder[T, U, CU, CCU]

  /** Transform the flow by an askerReplier task, binds to portals
    * `askerportals` and `replierportals`, executes the handler `f1` on regular
    * events, and executes handler `f2` on requests.
    *
    * @param askerportals
    *   the portals to bind to for asker
    * @param replierportals
    *   the portals to bind to for replier
    * @param f1
    *   the event handler
    * @param f2
    *   the request handler
    */
  @experimental
  def askerreplier[CCU, Req, Rep](
      askerportals: AtomicPortalRefKind[Req, Rep]*
  )(
      replierportals: AtomicPortalRefKind[Req, Rep]*
  )(
      f1: AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit
  )(
      f2: AskerReplierTaskContext[CU, CCU, Req, Rep] ?=> Req => Unit
  ): FlowBuilder[T, U, CU, CCU]

  //////////////////////////////////////////////////////////////////////////////
  // Portals DSL
  //////////////////////////////////////////////////////////////////////////////
  // It would be nice to move this to the DSL section, but couldn't get it to work with the same name `asker`.

  private[portals] class FlowBuilderAsker[CCU]:
    def apply[Req, Rep](
        portals: AtomicPortalRefKind[Req, Rep]*
    )(
        f: AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit
    ): FlowBuilder[T, U, CU, CCU] =
      asker[CCU, Req, Rep](portals: _*)(f)

  def asker[CCU]: FlowBuilderAsker[CCU] = new FlowBuilderAsker[CCU]

  private[portals] class FlowBuilderReplier[CCU]:
    def apply[Req, Rep](
        portals: AtomicPortalRefKind[Req, Rep]*
    )(
        f1: ProcessorTaskContext[CU, CCU] ?=> CU => Unit
    )(
        f2: ReplierTaskContext[CU, CCU, Req, Rep] ?=> Req => Unit
    ): FlowBuilder[T, U, CU, CCU] =
      replier[CCU, Req, Rep](portals: _*)(f1)(f2)

  def replier[CCU]: FlowBuilderReplier[CCU] = new FlowBuilderReplier[CCU]

end FlowBuilder // trait

object FlowBuilder:
  /** Internal API. Create a FlowBuilder from a workflow context, with `name`
    * being the latest modified task.
    */
  def apply[T, U, CT, CU](name: Option[String] = None)(using WorkflowBuilderContext[T, U]): FlowBuilder[T, U, CT, CU] =
    given FlowBuilderContext[T, U] = FlowBuilderContext[T, U](name)
    new FlowBuilderImpl[T, U, CT, CU]()
end FlowBuilder // object
