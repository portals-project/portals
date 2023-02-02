package portals

import scala.annotation.targetName

// format: off
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

  def freeze(): Workflow[T, U]

  //////////////////////////////////////////////////////////////////////////////
  // Sources and sinks
  //////////////////////////////////////////////////////////////////////////////

  private[portals] def source[CC >: T <: T](ref: AtomicStreamRefKind[T]): FlowBuilder[T, U, CC, CC]

  def sink[CC >: CU | U <: CU & U](): FlowBuilder[T, U, U, U]

  //////////////////////////////////////////////////////////////////////////////
  // Structural operations
  //////////////////////////////////////////////////////////////////////////////

  // TODO: deprecate and replaced by unionStar
  // def union[CCT, CCU](other: FlowBuilder[T, U, CCT, CCU]): FlowBuilder[T, U, CCU | CU, CCU | CU]

  def union(others: List[FlowBuilder[T, U, _, CU]]): FlowBuilder[T, U, CU, CU]

  def union(others: FlowBuilder[T, U, _, CU]*): FlowBuilder[T, U, CU, CU] = union(others.toList)

  def from[CU, CCU](others: FlowBuilder[T, U, _, CU]*)(
      task: GenericTask[CU, CCU, _, _]
  ): FlowBuilder[T, U, CU, CCU]

  //////////////////////////////////////////////////////////////////////////////
  // Stateful transformations
  //////////////////////////////////////////////////////////////////////////////

  def map[CCU](f: MapTaskContext[CU, CCU] ?=> CU => CCU): FlowBuilder[T, U, CU, CCU]

  // TODO: it should be possible to have generic keys
  def key(f: CU => Int): FlowBuilder[T, U, CU, CU]

  def task[CCU](taskBehavior: GenericTask[CU, CCU, _, _]): FlowBuilder[T, U, CU, CCU]

  def processor[CCU](f: ProcessorTaskContext[CU, CCU] ?=> CU => Unit): FlowBuilder[T, U, CU, CCU]

  def flatMap[CCU](f: MapTaskContext[CU, CCU] ?=> CU => Seq[CCU]): FlowBuilder[T, U, CU, CCU]

  def filter(p: CU => Boolean): FlowBuilder[T, U, CU, CU]

  def vsm[CCU](defaultTask: VSMTask[CU, CCU]): FlowBuilder[T, U, CU, CCU]

  def init[CCU](
      initFactory: ProcessorTaskContext[CU, CCU] ?=> GenericTask[CU, CCU, Nothing, Nothing]
  ): FlowBuilder[T, U, CU, CCU]

  //////////////////////////////////////////////////////////////////////////////
  // Useful operators
  //////////////////////////////////////////////////////////////////////////////

  def identity(): FlowBuilder[T, U, CU, CU]

  def logger(prefix: String = ""): FlowBuilder[T, U, CU, CU]

  /** Check the current type against the provided expected type.
    *
    * Compares FlowBuilder[T, U, C] with CCU, will succeed if C <: CCU <: C.
    */
  def checkExpectedType[CCU >: CU <: CU](): FlowBuilder[T, U, CT, CU]

  //////////////////////////////////////////////////////////////////////////////
  // Combinators
  //////////////////////////////////////////////////////////////////////////////

  def withName(name: String): FlowBuilder[T, U, CT, CU]

  def withOnNext(onNext: ProcessorTaskContext[CT, CU] ?=> CT => Unit): FlowBuilder[T, U, CT, CU]

  def withOnError(onError: ProcessorTaskContext[CT, CU] ?=> Throwable => Unit): FlowBuilder[T, U, CT, CU]

  def withOnComplete(onComplete: ProcessorTaskContext[CT, CU] ?=> Unit): FlowBuilder[T, U, CT, CU]

  def withOnAtomComplete(onAtomComplete: ProcessorTaskContext[CT, CU] ?=> Unit): FlowBuilder[T, U, CT, CU]

  def withWrapper(
      onNext: ProcessorTaskContext[CT, CU] ?=> (ProcessorTaskContext[CT, CU] ?=> CT => Unit) => CT => Unit
  ): FlowBuilder[T, U, CT, CU]

  def withStep(task: GenericTask[CT, CU, Nothing, Nothing]): FlowBuilder[T, U, CT, CU]

  def withLoop(count: Int)(task: GenericTask[CT, CU, Nothing, Nothing]): FlowBuilder[T, U, CT, CU]

  def withAndThen[CCU](task: GenericTask[CU, CCU, Nothing, Nothing]): FlowBuilder[T, U, CT, CCU]

  //////////////////////////////////////////////////////////////////////////////
  // All* Combinators
  //////////////////////////////////////////////////////////////////////////////

  def allWithOnAtomComplete[WT, WU](onAtomComplete: ProcessorTaskContext[WT, WU] ?=> Unit): FlowBuilder[T, U, CT, CU]

  def allWithWrapper[WT, WU](
      _onNext: ProcessorTaskContext[WT, WU] ?=> (ProcessorTaskContext[WT, WU] ?=> WT => Unit) => WT => Unit
  ): FlowBuilder[T | WT, U | WU, CT, CU]

  //////////////////////////////////////////////////////////////////////////////
  // Portals
  //////////////////////////////////////////////////////////////////////////////

  trait PortalFlowBuilder[Req, Rep]:
    def asker[CCU](
        f: AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit
    ): FlowBuilder[T, U, CU, CCU]

    def replier[CCU](f1: ProcessorTaskContext[CU, CCU] ?=> CU => Unit)(
        f2: ReplierTaskContext[CU, CCU, Req, Rep] ?=> Req => Unit
    ): FlowBuilder[T, U, CU, CCU]

  def portal[Req, Rep](portals: AtomicPortalRefType[Req, Rep]*): PortalFlowBuilder[Req, Rep]

end FlowBuilder // trait

object FlowBuilder:
  def apply[T, U, CT, CU](name: Option[String] = None)(using WorkflowBuilderContext[T, U]): FlowBuilder[T, U, CT, CU] =
    given FlowBuilderContext[T, U] = FlowBuilderContext[T, U](name)
    new FlowBuilderImpl[T, U, CT, CU]()
end FlowBuilder // object
