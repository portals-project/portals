package portals.compiler.phases.portal

import scala.collection.mutable

import portals.application.task.*
import portals.application.task.MapTaskStateExtension.*
import portals.compiler.phases.portal.RewritePortalEvents.*
import portals.runtime.executor.TaskExecutorImpl
import portals.runtime.WrappedEvents

/** Rewrite a task to use RewritePortalEvents instead. */
private[portals] object RewriteTask:
  private class RewriteTaskWrapper(_inner: GenericTask[Any, Any, Any, Any]):
    private var _tmp_oc: OutputCollector[Any, Any, Any, Any] = null
    private val _oc = OutputCollectorImpl[Any, Any, Any, Any]()
    // TODO: replace with static call instead, once merged
    private val taskexecutor = TaskExecutorImpl()

    private inline def pre_execute(
        event: Any,
        ctx: TaskContextImpl[Any, Any, Any, Any]
    ): Unit =
      _tmp_oc = ctx.outputCollector
      ctx.outputCollector = _oc
      event match
        case RewritePortalEvents.RewriteEvent(WrappedEvents.Ask(key, meta, event)) =>
          ctx.id = meta.id
          ctx.asker = meta.askingTask
          ctx.portal = meta.portal
          ctx.portalAsker = meta.askingWF
          ctx.askerKey = meta.askingKey
        case _ => ()

    private inline def post_execute(
        ctx: TaskContextImpl[Any, Any, Any, Any]
    ): Unit =
      val asks = _oc.getAskOutput()
      val reps = _oc.getRepOutput()
      val outs = _oc.getOutput()
      _oc.clear()
      _oc.clearAsks()
      _oc.clearReps()
      ctx.outputCollector = _tmp_oc
      asks.foreach { case ask @ WrappedEvents.Ask(portal, meta, event) =>
        ctx.emit(ask.toRewrite)
      }
      reps.foreach { case rep @ WrappedEvents.Reply(portal, meta, event) =>
        ctx.emit(rep.toRewrite)
      }
      outs.foreach {
        case evt @ WrappedEvents.Event(key, e) =>
          ctx.emit(e)
        case _ => ()
      }

    def execute(event: Any, ctx: TaskContextImpl[Any, Any, Any, Any]): Unit =
      this.pre_execute(event, ctx)
      event match
        case RewritePortalEvents.RewriteEvent(WrappedEvents.Ask(key, meta, event)) =>
          _inner match
            case ReplierTask(_, f2) =>
              f2(ctx.asInstanceOf[TaskContextImpl[Any, Any, Any, Any]])(event)
            case AskerReplierTask(_, f2) =>
              f2(ctx.asInstanceOf[TaskContextImpl[Any, Any, Any, Any]])(event)
            case _ => ???
        case RewritePortalEvents.RewriteEvent(WrappedEvents.Reply(key, meta, event)) =>
          taskexecutor.run_and_cleanup_reply(meta.id, event)(using
            ctx.asInstanceOf[TaskContextImpl[Any, Any, Any, Any]]
          )
        case event =>
          _inner.onNext(using ctx.asInstanceOf[TaskContextImpl[Any, Any, Any, Any]])(event)
      this.post_execute(ctx)
  end RewriteTaskWrapper

  def apply(inner: GenericTask[Any, Any, Any, Any]): GenericTask[Any, Any, Any, Any] =
    InitTask { ctx =>
      val wrapper = RewriteTaskWrapper(inner)

      ProcessorTask { ctx => event =>
        wrapper.execute(event, ctx.asInstanceOf[TaskContextImpl[Any, Any, Any, Any]])
      }.asInstanceOf[GenericTask[Any, Any, Any, Any]]
    }
end RewriteTask
