package portals.examples.bankaccount

import scala.annotation.experimental

import portals.*
import portals.application.ASTPrinter
import portals.system.Systems

@experimental
object BankAccountMain extends App:
  val app = BankAccount.app

  // ASTPrinter.println(app)

  val system = Systems.interpreter()

  val _ = system.launch(app)

  system.stepUntilComplete()

  system.shutdown()
