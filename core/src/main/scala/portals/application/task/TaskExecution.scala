package portals.application.task

import portals.application.task.GenericTask
import portals.application.task.InitTask
import portals.application.task.TaskContextImpl

// TODO: deprecated, remove, it is in TaskExecutorImpl now
object TaskExecution:
  /** Prepare a task behavior at runtime.
    *
    * This executes the initialization and returns the initialized task. This
    * needs to be called internally to initialize the task behavior before
    * execution.
    *
    * @param task
    *   task to be prepared
    * @param ctx
    *   task context
    * @return
    *   prepared task
    */
  private[portals] def prepareTask[T, U, Req, Rep](
      task: GenericTask[T, U, Req, Rep],
      ctx: TaskContextImpl[T, U, Req, Rep]
  ): GenericTask[T, U, Req, Rep] =
    /** internal recursive method */
    def prepareTaskRec(
        task: GenericTask[T, U, Req, Rep],
        ctx: TaskContextImpl[T, U, Req, Rep]
    ): GenericTask[T, U, Req, Rep] =
      // FIXME: (low prio) this will fail if we have a init nested inside of a normal behavior
      task match
        case InitTask(initFactory) => prepareTaskRec(initFactory(ctx), ctx)
        case _ => task

    /** prepare the task, recursively performing initialization */
    prepareTaskRec(task, ctx)
  end prepareTask // def
