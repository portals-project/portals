package portals.examples.distributed.bankaccount

import scala.annotation.experimental

import portals.api.dsl.CustomTask
import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*

@experimental
object BankAccount:
  import BankAccountEvents.*

  val app = PortalsApp("BankAccount") {
    // generated stream of transactions
    val txnOps = Generators.fromIteratorOfIterators(BankAccountData.txnIter)

    // bank account service portal
    val account = Portal[Req, Rep]("account", keyFrom)

    // bank account workflow
    val accountWorkflow = Workflows[Nothing, Nothing]("accountWorkflow")
      .source(Generators.empty.stream)
      .task[Nothing] {
        // TODO: this looks ugly, change the way we use CustomTasks :/
        CustomTask.askerReplier(account)(account) { () => new BankAccountTasks.AccountTask(account) }
      }
      .sink()
      .freeze()

    // trigger workflow
    val _ = Workflows[Req, Nothing]("txns")
      .source(txnOps.stream)
      .task(BankAccountTasks.triggerTask(account))
      .filter(_.isInstanceOf[SagaSuccess[AccountOperation]])
      .logger()
      .empty[Nothing]()
      .sink()
      .freeze()
  }
