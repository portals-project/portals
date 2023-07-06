package portals.examples.bankaccount

import scala.annotation.experimental

object BankAccountEvents:
  //////////////////////////////////////////////////////////////////////////////
  // Saga Events
  //////////////////////////////////////////////////////////////////////////////

  // Saga Operations
  sealed trait SagaOperation[T]

  // Saga Requests
  sealed trait SagaRequest[T] extends SagaOperation[T]

  case class Saga[T](head: T, tail: List[T]) extends SagaRequest[T]

  // Saga Replies
  sealed trait SagaReply[T] extends SagaOperation[T]

  case class SagaSuccess[T](saga: Option[Saga[T]] = None) extends SagaReply[T]
  case class SagaAbort[T](saga: Option[Saga[T]] = None) extends SagaReply[T]

  //////////////////////////////////////////////////////////////////////////////
  // Account Events
  //////////////////////////////////////////////////////////////////////////////

  // Account Operations
  sealed trait AccountOperation

  case class Deposit(id: Long, amount: Int) extends AccountOperation
  case class Withdraw(id: Long, amount: Int) extends AccountOperation

  //////////////////////////////////////////////////////////////////////////////
  // Key Extractors
  //////////////////////////////////////////////////////////////////////////////

  def keyFrom(op: AccountOperation): Long = op match
    case Deposit(id, _) => id
    case Withdraw(id, _) => id

  def keyFrom(saga: SagaOperation[AccountOperation]): Long = saga match
    case Saga(head, _) =>
      keyFrom(head)
    case SagaSuccess(Some(Saga(head, _))) =>
      keyFrom(head)
    case SagaAbort(Some(Saga(head, _))) =>
      keyFrom(head)
    case _ => ???
