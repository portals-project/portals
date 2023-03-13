package portals.examples.distributed.bankaccount

import scala.annotation.experimental

import portals.*
import portals.api.builder.TaskBuilder
import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.task.AskerReplierTaskContext
import portals.application.task.AskerTaskContext
import portals.application.task.LoggingTaskContext
import portals.application.task.PerKeyState
import portals.application.task.StatefulTaskContext
import portals.application.AtomicPortalRefKind

@experimental
object BankAccountTasks:
  import BankAccountEvents.*

  type WithStatefulTaskContext[T] = StatefulTaskContext ?=> T

  //////////////////////////////////////////////////////////////////////////////
  // Account Task
  //////////////////////////////////////////////////////////////////////////////

  // Task that handles bank accounts on a per-key basis
  @experimental
  class AccountTask(portal: AtomicPortalRefKind[Req, Rep]) extends CustomAskerReplierTask[Nothing, Nothing, Req, Rep]:

    // Account state
    lazy val state: WithStatefulTaskContext[PerKeyState[Int]] = PerKeyState[Int]("state", 0)

    // Account lock
    lazy val lock: WithStatefulTaskContext[PerKeyState[Boolean]] = PerKeyState[Boolean]("lock", false)

    // Execute an account operation on the state
    private def executeOp(op: AccountOperation)(using StatefulTaskContext, LoggingTaskContext): Unit =
      if BankAccountConfig.LOGGING then log.info(s"Executing operation: $op")
      op match
        case Deposit(id, amount) =>
          if BankAccountConfig.LOGGING then log.info("Deposit " + amount + " to account " + id)
          state.set(state.get() + amount)
        case Withdraw(id, amount) =>
          if BankAccountConfig.LOGGING then log.info("Withdraw " + amount + " from account " + id)
          state.set(state.get() - amount)

    // Does not handle onNext events, does nothing, produces nothing
    override def onNext(using AskerTaskContext[Nothing, Nothing, Req, Rep])(event: Nothing): Unit = ()

    // Handle onAsk events
    override def onAsk(using ctx: AskerReplierTaskContext[Nothing, Nothing, Req, Rep])(req: Req): Unit =
      // If the account is locked, abort the transaction
      if lock.get() then
        if BankAccountConfig.LOGGING then ctx.log.info(s"Account is locked, aborting transaction: $req")
        reply(SagaAbort())
      // Else, pre-commit the transaction, and forward Saga
      else
        // Lock the account
        lock.set(true)

        // If the transaction tail is empty, then we can execute the transaction, and reply with success
        if req.tail.isEmpty then
          if BankAccountConfig.LOGGING then ctx.log.info(s"Transaction success, executing operation: ${req.head}")
          executeOp(req.head)
          reply(SagaSuccess())

          // Unlock the account
          lock.set(false)

        // Otherwise, the transaction tail is not empty, so we need to forward the Saga before we can execute it
        else

          // Forward the Saga tail to the next account
          val next = Saga(req.tail.head, req.tail.tail)
          if BankAccountConfig.LOGGING then ctx.log.info(s"Next operation in transaction: $next")
          val f = ask(portal)(next)

          // Wait for a reply from the next account, if the next one has succeded, then we can also commit, else abort
          f.await {
            f.value.get match

              // Transaction succeeded, execute the operation, and reply with success
              case SagaSuccess(_) =>
                if BankAccountConfig.LOGGING then ctx.log.info(s"Transaction success, executing operation: ${req.head}")
                executeOp(req.head)
                reply(SagaSuccess())

                // Unlock the account
                lock.set(false)

              // Transaction aborted, abort the transaction, and reply with abort
              case SagaAbort(_) =>
                if BankAccountConfig.LOGGING then ctx.log.info(s"Transaction aborted: ${req.head}")
                reply(SagaAbort())

                // Unlock the account
                lock.set(false)
          }

  //////////////////////////////////////////////////////////////////////////////
  // Trigger Task
  //////////////////////////////////////////////////////////////////////////////

  // Task that triggers the account Portal with requests, and emits the replies
  def triggerTask(account: AtomicPortalRefKind[Req, Rep]) =
    TaskBuilder.asker[Req, Rep, Req, Rep](account) { case saga @ Saga(head, tail) =>
      if BankAccountConfig.LOGGING then log.info(s"Asking for transaction: $saga")
      val f = ask(account)(saga)
      await(f) {
        f.value.get match
          case SagaSuccess(_) =>
            if BankAccountConfig.LOGGING then ctx.log.info(s"Whole transaction success: $saga")
            ctx.emit(SagaSuccess(Some(saga)))
          case SagaAbort(_) =>
            ctx.emit(SagaAbort(Some(saga)))
            if BankAccountConfig.LOGGING then ctx.log.info(s"Whole transaction aborted: $saga")
      }
    }
