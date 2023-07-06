package portals.examples.bankaccount

import scala.annotation.experimental

import portals.*
import portals.application.ASTPrinter
import portals.system.Systems

object BankAccountMain extends App:
  val app = BankAccount.app
  val system = Systems.test()
  val _ = system.launch(app)
  system.stepUntilComplete()
  system.shutdown()
