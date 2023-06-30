package portals.examples.bankaccount

import scala.annotation.experimental
import scala.util.Random

@experimental
object BankAccountData:
  import BankAccountConfig.*
  import BankAccountEvents.*

  val rand = new scala.util.Random

  private def op: AccountOperation =
    math.abs(rand.nextInt() % 2) match
      case 0 => Deposit(rand.nextInt(N_ACCOUNTS), rand.nextInt(100))
      case 1 => Withdraw(rand.nextInt(N_ACCOUNTS), rand.nextInt(100))

  def txnIter: Iterator[Iterator[Req]] =
    Iterator
      .fill(N_EVENTS) {
        Saga(op, List.fill(N_OPS_PER_SAGA - 1)(op))
      }
      .grouped(5)
      .map(_.iterator)
