package portals

import portals.application.task.MapTaskStateExtension.*
import portals.application.task.PerTaskState

object TaskExecution:
  /** Prepare a task behavior at runtime. This executes the initialization and
    * returns the initialized task. This needs to be called internally to
    * initialize the task behavior before execution.
    */
  // format: off
  private[portals] def prepareTask[T, U, Req, Rep](task: GenericTask[T, U, Req, Rep], ctx: TaskContextImpl[T, U, Req, Rep]): GenericTask[T, U, Req, Rep] =
    /** internal recursive method */
    def prepareTaskRec(task: GenericTask[T, U, Req, Rep], ctx: TaskContextImpl[T, U, Req, Rep]): GenericTask[T, U, Req, Rep] =
      // FIXME: (low prio) this will fail if we have a init nested inside of a normal behavior
      task match
        case InitTask(initFactory) => prepareTaskRec(initFactory(ctx), ctx)
        case _ => task

    /** prepare the task, recursively performing initialization */
    prepareTaskRec(task, ctx)
  end prepareTask // def
  // format: on

  private[portals] def run_and_cleanup_reply[T, U, Req, Rep](id: Int, r: Rep)(using
      actx: AskerTaskContext[T, U, Req, Rep]
  ): Unit =
    lazy val _futures: AskerTaskContext[_, _, _, Rep] ?=> PerTaskState[Map[Int, Rep]] =
      PerTaskState[Map[Int, Rep]]("futures", Map.empty)
    lazy val _continuations: AskerTaskContext[T, U, Req, Rep] ?=> PerTaskState[Map[Int, Continuation[T, U, Req, Rep]]] =
      PerTaskState[Map[Int, Continuation[T, U, Req, Rep]]]("continuations", Map.empty)
    // set future
    _futures.update(id, r)
    // run continuation
    _continuations.get(id) match
      case Some(continuation) => continuation(using actx)
      case None => () // do nothing, no continuation was saved for this future
    // cleanup future
    _futures.remove(id)
    // cleanup continuation
    _continuations.remove(id)
  end run_and_cleanup_reply // def
