package pods.workflows

sealed trait Task[I, O]:
  private[pods] val tctx: TaskContext[I, O]
  private[pods] val worker: Worker[I, O]
  private[pods] def close(): Unit

  def iref = tctx.mainiref
  def oref = tctx.mainoref

private[pods] class TaskImpl[I, O](taskBehavior: TaskBehavior[I, O]) extends Task[I, O]:
  import Workers.*
  private[pods] val tctx: TaskContext[I, O] = TaskContext[I, O]()
  private[pods] val worker = Workers[I, O]()
    .withOnNext(event => taskBehavior.onNext(tctx)(event))
    .withOnError(throwable => taskBehavior.onError(tctx)(throwable))
    .withOnComplete(() => taskBehavior.onComplete(tctx))
    .build()
  tctx.mainiref.worker.subscribe(this.worker)
  tctx.iref.worker.subscribe(this.worker)
  this.worker.subscribe(tctx.mainoref.worker)

  private[pods] def close(): Unit =
    tctx.iref.close()
    tctx.mainiref.close()
    worker.close()
    tctx.mainoref.close()

object Tasks:
  def apply[I, O](behavior: TaskBehavior[I, O]): Task[I, O] =
    new TaskImpl[I, O](behavior)

  def connect[T](task1: Task[_, T], task2: Task[T, _]): Unit =
    task1.tctx.mainoref.subscribe(task2.tctx.mainiref.asInstanceOf)
