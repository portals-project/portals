package portals.examples.distributed.bankaccount

import scala.annotation.experimental

@experimental
object BankAccountEvents:
  //////////////////////////////////////////////////////////////////////////////
  // Saga Events
  //////////////////////////////////////////////////////////////////////////////

  // Saga Operations
  @experimental sealed trait SagaOperation[T]

  // Saga Requests
  @experimental sealed trait SagaRequest[T] extends SagaOperation[T]

  @experimental case class Saga[T](head: T, tail: List[T]) extends SagaRequest[T]

  // Saga Replies
  @experimental sealed trait SagaReply[T] extends SagaOperation[T]

  @experimental case class SagaSuccess[T](saga: Option[Saga[T]] = None) extends SagaReply[T]
  @experimental case class SagaAbort[T](saga: Option[Saga[T]] = None) extends SagaReply[T]

  //////////////////////////////////////////////////////////////////////////////
  // Account Events
  //////////////////////////////////////////////////////////////////////////////

  // Account Operations
  sealed trait AccountOperation

  @experimental case class Deposit(id: Long, amount: Int) extends AccountOperation
  @experimental case class Withdraw(id: Long, amount: Int) extends AccountOperation

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

  //////////////////////////////////////////////////////////////////////////////
  // Types
  //////////////////////////////////////////////////////////////////////////////

  type Req = Saga[AccountOperation]
  type Rep = SagaReply[AccountOperation]
  type SagaOp = SagaOperation[AccountOperation]
