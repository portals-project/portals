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

// Task that handles bank accounts on a per-key basis
object AccountTask:
  type I = Nothing
  type O = Nothing
  type Req = Saga[AccountOperation]
  type Res = SagaReply[AccountOperation]
  type Context = AskerReplierTaskContext[Nothing, Nothing, Req, Res]
  type AskerContext = AskerTaskContext[Nothing, Nothing, Req, Res]
  type PortalRef = AtomicPortalRefKind[Req, Res]
  type Task = GenericTask[Nothing, Nothing, Req, Res]

  // Account state
  private final val state: PerKeyState[Int] =
    PerKeyState[Int]("state", BankAccountConfig.STARTING_BALANCE)

  // Account lock
  private final val lock: PerKeyState[Boolean] =
    PerKeyState[Boolean]("lock", false)

  // Check if an account operation can be executed on the state
  private def canExecuteOp(op: AccountOperation)(using Context): Boolean =
    if BankAccountConfig.LOGGING then log.info(s"Checking if can execute: $op")
    op match
      case Deposit(_, _) =>
        true
      case Withdraw(id, amount) =>
        state.get() >= amount

  // Execute an account operation on the state
  private def executeOp(op: AccountOperation)(using Context): Unit =
    if BankAccountConfig.LOGGING then log.info(s"Executing operation: $op")
    op match
      case Deposit(id, amount) =>
        if BankAccountConfig.LOGGING then log.info("Deposit " + amount + " to account " + id)
        state.set(state.get() + amount)
      case Withdraw(id, amount) =>
        if BankAccountConfig.LOGGING then log.info("Withdraw " + amount + " from account " + id)
        state.set(state.get() - amount)

  // Does not handle onNext events, does nothing, produces nothing
  private def onNext(event: I)(using AskerContext): Unit = ()

  // Handle onAsk events
  private def onAsk(portal: PortalRef)(req: Req)(using Context): Unit =
    // If the account is locked, abort the transaction
    if lock.get() then
      if BankAccountConfig.LOGGING then ctx.log.info(s"Account is locked, aborting transaction: $req")
      reply(SagaAbort())
    // Else, pre-commit the transaction, and forward Saga
    else
      // Lock the account
      lock.set(true)

      // Check if we can pre-commit / execute the operation, else abort
      if !canExecuteOp(req.head) then reply(SagaAbort())
      else { // open bracket here needed as otherwise scalafmt will unindent subsequent code

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
      }

  // Create a new account task
  def apply(portal: PortalRef): Task =
    TaskBuilder.askerreplier[Nothing, Nothing, Req, Res](portal)(portal)(onNext)(onAsk(portal))

end AccountTask // object
