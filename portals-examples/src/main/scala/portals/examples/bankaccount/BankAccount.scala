package portals.examples.bankaccount

import scala.annotation.experimental

import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.Application
import portals.examples.bankaccount.BankAccountEvents.*

////////////////////////////////////////////////////////////////////////////////
// Bank Account
////////////////////////////////////////////////////////////////////////////////
object BankAccount:

  // the bank account application
  def app: Application = PortalsApp("BankAccount"):

    // generated stream of transactions
    val txnOps = Generators.fromIteratorOfIterators(BankAccountData.txnIter)

    // bank account service portal
    val account = Portal[Saga[AccountOperation], SagaReply[AccountOperation]]("account", keyFrom)

    // bank account workflow
    val accountWorkflow = Workflows[Nothing, Nothing]("accountWorkflow")
      .source(Generators.empty.stream) // no input
      .task[Nothing](AccountTask(account))
      .sink()
      .freeze()

    // trigger workflow
    val _ = Workflows[Saga[AccountOperation], Nothing]("txns")
      .source(txnOps.stream)
      .task(TriggerTask(account))
      .filter(_.isInstanceOf[SagaSuccess[AccountOperation]])
      .logger()
      .empty[Nothing]() // consume the stream
      .sink()
      .freeze()

  end app
end BankAccount
