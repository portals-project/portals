package portals

import scala.annotation.targetName

trait FlowBuilder[T, U]:
  //////////////////////////////////////////////////////////////////////////////
  // Freezing
  //////////////////////////////////////////////////////////////////////////////

  def freeze(): Workflow[T, U]

  //////////////////////////////////////////////////////////////////////////////
  // Sources and sinks
  //////////////////////////////////////////////////////////////////////////////

  private[portals] def source[TT >: T <: T](name: String = null): FlowBuilder[T, U]

  @targetName("sourceFromRef")
  private[portals] def source[TT >: T <: T](ref: AtomicStreamRef[T]): FlowBuilder[T, U]

  def sink[TT >: T | U <: T & U](name: String = null): FlowBuilder[T, U]

  //////////////////////////////////////////////////////////////////////////////
  // Structural operations
  //////////////////////////////////////////////////////////////////////////////

  def union[TT](other: FlowBuilder[TT, U]): FlowBuilder[T | TT, U]

  //////////////////////////////////////////////////////////////////////////////
  // Stateful transformations
  //////////////////////////////////////////////////////////////////////////////

  def map[TT](f: MapTaskContext[T, TT] ?=> T => TT): FlowBuilder[TT, U]

  // TODO: it should be possible to have generic keys
  def key(f: T => Int): FlowBuilder[T, U]

  def task[TT](taskBehavior: Task[T, TT]): FlowBuilder[TT, U]

  def processor[TT](f: TaskContext[T, TT] ?=> T => Unit): FlowBuilder[TT, U]

  def flatMap[TT](f: MapTaskContext[T, TT] ?=> T => Seq[TT]): FlowBuilder[TT, U]

  def filter(p: T => Boolean): FlowBuilder[T, U]

  def vsm[TT](defaultTask: Task[T, TT]): FlowBuilder[TT, U]

  def init[TT](initFactory: TaskContext[T, TT] ?=> Task[T, TT]): FlowBuilder[TT, U]

  //////////////////////////////////////////////////////////////////////////////
  // Useful operators
  //////////////////////////////////////////////////////////////////////////////

  def identity(): FlowBuilder[T, U]

  def logger(prefix: String = ""): FlowBuilder[T, U]

  /** Check the current type against the provided expected type.
    *
    * Compares FlowBuilder[T, U] with FlowBuilder[TT, UU], will succeed if T <: TT <: T and U <: UU <: U.
    */
  def checkExpectedType[TT >: T <: T, UU >: U <: U](): FlowBuilder[T, U]

  //////////////////////////////////////////////////////////////////////////////
  // Combinators
  //////////////////////////////////////////////////////////////////////////////

  def withName(name: String): FlowBuilder[T, U]

  def withOnNext(onNext: TaskContext[T, U] ?=> T => Task[T, U]): FlowBuilder[T, U]

  def withOnError(onError: TaskContext[T, U] ?=> Throwable => Task[T, U]): FlowBuilder[T, U]

  def withOnComplete(onComplete: TaskContext[T, U] ?=> Task[T, U]): FlowBuilder[T, U]

  def withOnAtomComplete(onAtomComplete: TaskContext[T, U] ?=> Task[T, U]): FlowBuilder[T, U]

  def withWrapper(
      onNext: TaskContext[T, U] ?=> (TaskContext[T, U] ?=> T => Task[T, U]) => T => Unit
  ): FlowBuilder[T, U]

  def withStep(task: Task[T, U]): FlowBuilder[T, U]

  def withLoop(count: Int)(task: Task[T, U]): FlowBuilder[T, U]

  def withAndThen[TT](task: Task[U, TT]): FlowBuilder[TT, U]

end FlowBuilder // trait

object FlowBuilder:
  def apply[T, U](name: Option[String] = None)(using WorkflowBuilderContext[T, U]): FlowBuilder[T, U] =
    given FlowBuilderContext[T, U] = FlowBuilderContext[T, U](name)
    new FlowBuilderImpl[T, U]()
end FlowBuilder // object
