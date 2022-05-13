package pods.workflows

sealed trait Task[I, O]:
  private[pods] val tctx: TaskContext[I, O]
  private[pods] val worker: Worker[I, O]

private[pods] class TaskImpl[I, O](taskBehavior: TaskBehavior[I, O]) extends Task[I, O]:
  import Workers.*
  private[pods] val tctx: TaskContext[I, O] = TaskContext[I, O]()
  private[pods] val worker = Workers[I, O]()
    .withOnNext(event => taskBehavior.onNext(tctx)(event))
    .withOnError(throwable => taskBehavior.onError(tctx)(throwable))
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
