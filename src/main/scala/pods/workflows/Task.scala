package pods.workflows

import org.slf4j.Logger
import org.slf4j.LoggerFactory

private[pods] sealed trait TaskContextState[K, V]:
  def get(k: K): Option[V]
  def set(k: K, v: V): Unit
  def del(k: K): Unit

private[pods] class TaskContextStateImpl[K, V] extends TaskContextState[K, V]:
  private var map: Map[K, V] = Map.empty
  def get(k: K): Option[V] = map.get(k)
  def set(k: K, v: V): Unit = map += (k -> v)
  def del(k: K): Unit = map -= k

private[pods] object TaskContextState:
  def apply[K, V](): TaskContextState[K, V] =
    new TaskContextStateImpl[K, V]

private[pods] sealed trait TaskContext[I, O]:
  private[pods] val ic: IChannel[I]
  private[pods] val oc: OChannel[O]
  def self: IChannel[I] // input channel for external requests
  def emit(event: O): Unit
  // def emit(oc: OChannel[O], event: O): Unit // tasks are single-input single-output for now :)
  def emit[T](ic: IChannel[_], event: T): Unit
  def state: TaskContextState[Any, Any]
  def log: Logger

private[pods] class TaskContextImpl[I, O] extends TaskContext[I, O]:
  private[pods] val ic: IChannel[I] = IChannel[I]()
  private[pods] val oc: OChannel[O] = OChannel[O]()
  val self: IChannel[I] = IChannel[I]()
  def emit(event: O): Unit =
    oc.submit(event)
  def emit[T](ic: IChannel[_], event: T): Unit =
    val newoc = OChannel[T]()
    newoc.subscribe(ic.asInstanceOf)
    newoc.submit(event)
    newoc.close()

  def state: TaskContextState[Any, Any] = TaskContextState()
  def log: Logger = LoggerFactory.getLogger(this.getClass)

object TaskContext:
  def apply[I, O](): TaskContext[I, O] =
    new TaskContextImpl[I, O]

private[pods] trait TaskBehavior[I, O]:
  def onNext(tctx: TaskContext[I, O], t: I): TaskBehavior[I, O] = ???
  def onError(tctx: TaskContext[I, O], t: Throwable): TaskBehavior[I, O] = ???
  def onComplete(tctx: TaskContext[I, O]): TaskBehavior[I, O] = ???

object TaskBehaviors:
  // behavior factory for handling an incoming event and context
  def processor[I, O](
      onNext: TaskContext[I, O] ?=> I => TaskBehavior[I, O]
  ): TaskBehavior[I, O] =
    ProcessBehavior[I, O](tctx => onNext(using tctx))

  private[pods] case class ProcessBehavior[I, O](
      _onNext: TaskContext[I, O] => I => TaskBehavior[I, O]
  ) extends TaskBehavior[I, O]:
    override def onNext(tctx: TaskContext[I, O], event: I): TaskBehavior[I, O] =
      _onNext(tctx)(event)

  def map[I, O](f: I => O): TaskBehavior[I, O] =
    StatelessProcessBehavior[I, O](tctx => x => tctx.emit(f(x)))

  def flatMap[I, O](f: I => TraversableOnce[O]): TaskBehavior[I, O] =
    StatelessProcessBehavior[I, O](tctx => x => f(x).foreach(tctx.emit(_)))

  private[pods] case class StatelessProcessBehavior[I, O](
      _onNext: TaskContext[I, O] => I => Unit
  ) extends TaskBehavior[I, O]:
    override def onNext(tctx: TaskContext[I, O], event: I): TaskBehavior[I, O] =
      _onNext(tctx)(event)
      TaskBehaviors.same

  def same[T, S]: TaskBehavior[T, S] =
    // same behavior is compatible with previous behavior
    SameBehavior.asInstanceOf[TaskBehavior[T, S]]

  private[pods] case object SameBehavior extends TaskBehavior[Nothing, Nothing]
  // this is fine, the methods are ignored as we reuse the previous behavior

  def identity[T]: TaskBehavior[T, T] =
    IdentityBehavior[T]()

  private[pods] case class IdentityBehavior[T]() extends TaskBehavior[T, T]:
    override def onNext(tctx: TaskContext[T, T], event: T): TaskBehavior[T, T] =
      tctx.emit(event)
      TaskBehaviors.same

sealed trait Task[I, O]:
  private[pods] val tctx: TaskContext[I, O]
  private[pods] val worker: Worker[I, O]

private[pods] class TaskImpl[I, O](taskBehavior: TaskBehavior[I, O])
    extends Task[I, O]:
  import Workers.*
  private[pods] val tctx: TaskContext[I, O] = TaskContext[I, O]()
  private[pods] val worker = Workers[I, O]()
    .withOnNext(event => taskBehavior.onNext(tctx, event))
    .withOnError(throwable => taskBehavior.onError(tctx, throwable))
    .withOnComplete(() => taskBehavior.onComplete(tctx))
    .build()
  tctx.ic.worker.subscribe(this.worker)
  tctx.self.worker.subscribe(this.worker)
  this.worker.subscribe(tctx.oc.worker)

object Tasks:
  def apply[I, O](behavior: TaskBehavior[I, O]): Task[I, O] =
    new TaskImpl[I, O](behavior)

  def connect[T](task1: Task[_, T], task2: Task[T, _]): Unit =
    task1.tctx.oc.subscribe(task2.tctx.ic)

@main def testTask() =
  val task1 = Tasks(TaskBehaviors.processor[Int, Int]({ tctx ?=> x =>
    tctx.log.info(s"task1: $x")
    tctx.emit(x + 1)
    TaskBehaviors.same
  }))

  val task2 = Tasks(TaskBehaviors.processor[Int, Int]({ tctx ?=> x =>
    tctx.log.info(s"task2: $x")
    tctx.emit(x + 1)
    TaskBehaviors.same
  }))

  Tasks.connect(task1, task2)

  println("should log twice, once for each task")

  task1.tctx.ic.worker.submit(1)

  Thread.sleep(100)
