package portals.compiler.phases.portal

import scala.collection.mutable
import scala.util.Try

import portals.api.builder.*
import portals.api.builder.TaskExtensions.*
import portals.application.*
import portals.application.task.*
import portals.application.task.MapTaskStateExtension.*
import portals.compiler.*
import portals.runtime.executor.*
import portals.runtime.WrappedEvents
import portals.runtime.WrappedEvents.Atom
import portals.util.Common.Types.*

/** Rewrite workflows with portal dependencies. */
private[portals] object RewriteWorkflowsSubPhase extends CompilerSubPhase[Application, Application]:

  import RewritePortalEvents.*
  import RewritePortalHelpers.*

  override def run(application: Application)(using ctx: CompilerContext): Application =
    application.copy(
      workflows = application.workflows
        .map(wf =>
          // if the workflow is connected to a portal
          RewritePortalHelpers.hasPortal(wf) match
            // then rewrite the workflow
            case true =>
              Try(wf)
                // 1) Replace the source with a proxy source, which filters out the asks/replies
                .map(step1)
                // 2) Replace asker/repliers with regular tasks
                .map(step2)
                // 3) Topologically sort the tasks
                .map(step3)
                .get
            case false => wf
        )
    )
  end run

  /** Replace the source with a proxy source, which filters out the
    * asks/replies.
    *
    * 1) insert a task which filters out events for the original source path.
    *
    * 2) replace the source with a new source id.
    *
    * 3) insert a connection form the new source id to the old source.
    */
  private def step1(workflow: Workflow[_, _]): Workflow[_, _] =
    given mwf: MutWorkflow = MutWorkflow(workflow)

    val sourceId = RewritePortalHelpers.freshId
    val oldSource = mwf.wf.source
    RewritePortalHelpers.updateSource(sourceId)

    val filter = TaskBuilder.filter[Any] {
      case RewritePortalEvents.RewriteEvent(WrappedEvents.Ask(_, _, _)) => false
      case RewritePortalEvents.RewriteEvent(WrappedEvents.Reply(_, _, _)) => false
      case _ => true
    }
    RewritePortalHelpers.addTask(oldSource, filter)
    RewritePortalHelpers.appendConnection(sourceId, oldSource)

    mwf.wf
  end step1

  /** Replace asker/replier tasks with regular tasks.
    *
    * For each asker/replier task:
    *
    * 1) insert a task which filters out events for the original asker/replier
    * path.
    *
    * 2) insert a new proxy task which wraps the asker/replier.
    *
    * 3) insert a connection from the filter to the new wrapping task.
    *
    * 4) rewrite all existing connections to point at the new wrapping task.
    *
    * 5) filter out the regular events for this new task, use the original task
    * path/name for this filter.
    *
    * 6) filter out the asks/replies for this new task, and connect this to the
    * sink.
    */
  private def step2(workflow: Workflow[_, _]): Workflow[_, _] =
    given mwf: MutWorkflow = MutWorkflow(workflow)

    // for each askers, repliers, askerrepliers
    extractTasksWithAsks(workflow).toList
      .appendedAll(extractTasksWithRepliers(workflow).toList)
      .appendedAll(extractTasksWithAskerRepliers(workflow).toList)
      .foreach { (path, task) =>
        // insert a task which filters out events for the original asker/replier path.
        val filterId = RewritePortalHelpers.freshId
        val newTaskId = RewritePortalHelpers.freshId
        val filters = TaskBuilder.filter[Any] {
          case RewritePortalEvents.RewriteEvent(WrappedEvents.Reply(_, meta, _)) if meta.askingTask == newTaskId =>
            true
          case RewritePortalEvents.RewriteEvent(WrappedEvents.Ask(_, meta, _)) =>
            RewritePortalHelpers.taskContainsReplyers(task, meta.portal)
          case _ => false
        }
        RewritePortalHelpers.addTask(filterId, filters)
        RewritePortalHelpers.appendConnection(mwf.wf.source, filterId)

        // insert a new proxy task which wraps the asker/replier.
        val newTask = RewriteTask.apply(task.asInstanceOf[GenericTask[Any, Any, Any, Any]])
        RewritePortalHelpers.addTask(newTaskId, newTask)

        // insert a connection from the filter to the new wrapping task
        RewritePortalHelpers.appendConnection(filterId, newTaskId)

        // rewrite all connections to point to the new asker instead of the old asker
        mwf.wf = mwf.wf.copy(
          connections = mwf.wf.connections.map {
            case (from, p) if p == path => from -> newTaskId
            case x => x
          }
        )

        // filter out the regular events for this new task, use the original task path/name for this filter
        val filter2 = TaskBuilder.filter[Any] {
          case RewritePortalEvents.RewriteEvent(WrappedEvents.Ask(_, _, _)) => false
          case RewritePortalEvents.RewriteEvent(WrappedEvents.Reply(_, _, _)) => false
          case _ => true
        }
        RewritePortalHelpers.addTask(path, filter2)
        RewritePortalHelpers.appendConnection(newTaskId, path)

        // filter out the asks/replies for this new task, and connect this to the sink
        val filter3Id = RewritePortalHelpers.freshId
        val filter3 = TaskBuilder.filter[Any] {
          case RewritePortalEvents.RewriteEvent(WrappedEvents.Ask(_, _, _)) => true
          case RewritePortalEvents.RewriteEvent(WrappedEvents.Reply(_, _, _)) => true
          case _ => false
        }
        RewritePortalHelpers.addTask(filter3Id, filter3)
        RewritePortalHelpers.appendConnection(newTaskId, filter3Id)
        RewritePortalHelpers.appendConnection(filter3Id, mwf.wf.sink)

      }
    mwf.wf
  end step2

  /** Sort tasks in reverse topological order. */
  private def step3(workflow: Workflow[_, _]): Workflow[_, _] =
    workflow.copy(
      connections =
        // in reverse topological order
        RewritePortalHelpers.topologicalSort(workflow.connections).reverse
    )
  end step3
