package portals.examples.bankaccount

import portals.api.builder.TaskBuilder
import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.application.task.AskerReplierTaskContext
import portals.application.task.AskerTaskContext
import portals.application.task.GenericTask
import portals.application.task.LoggingTaskContext
import portals.application.task.PerKeyState
import portals.application.task.StatefulTaskContext
import portals.application.AtomicPortalRefKind
import portals.examples.bankaccount.BankAccountEvents.*

// Task that triggers the account Portal with requests, and emits the replies
object TriggerTask:
  type I = Saga[AccountOperation]
  type O = SagaReply[AccountOperation]
  type Req = Saga[AccountOperation]
  type Res = SagaReply[AccountOperation]
  type Context = AskerTaskContext[Req, Res, Req, Res]
  type PortalRef = AtomicPortalRefKind[Req, Res]
  type Task = GenericTask[Req, Res, Req, Res]

  // Handle onNext events
  private def onNext(portal: PortalRef)(event: I)(using Context): Unit =
    event match
      case saga @ Saga(head, tail) =>
        if BankAccountConfig.LOGGING then log.info(s"Asking for transaction: $saga")

        // Send the saga transaction to the account portal
        val f = ask(portal)(saga)

        // Wait for the reply (async)
        await(f) {
          f.value.get match
            // Transaction succeeded, emit SagaSuccess
            case SagaSuccess(_) =>
              if BankAccountConfig.LOGGING then ctx.log.info(s"Whole transaction success: $saga")
              ctx.emit(SagaSuccess(Some(saga)))

            // Transaction aborted, emit SagaAbort
            case SagaAbort(_) =>
              if BankAccountConfig.LOGGING then ctx.log.info(s"Whole transaction aborted: $saga")
              ctx.emit(SagaAbort(Some(saga)))
        }

  // Create a new trigger task
  def apply(portal: PortalRef): Task =
    Tasks.asker(portal)(onNext(portal))

end TriggerTask
