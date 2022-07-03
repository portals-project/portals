package pods.workflows

import java.util.concurrent.Flow.Subscription

trait OperatorSpec[T, U]: 
  final private[pods] def submit(item: U): WithContext[T, U, Unit] = summon[OperatorCtx[T, U]].submit(item)
  final private[pods] def fuse()         : WithContext[T, U, Unit] = summon[OperatorCtx[T, U]].fuse()
  final private[pods] def seal()         : WithContext[T, U, Unit] = summon[OperatorCtx[T, U]].seal()
  
  def onNext(subscriptionId: Int, item: T): WithContext[T, U, Unit]
  def onComplete(subscriptionId: Int): WithContext[T, U, Unit]
  def onError(subscriptionId: Int, error: Throwable): WithContext[T, U, Unit]
  def onSubscribe(subscriptionId: Int, subscription: Subscription): WithContext[T, U, Unit]
  def onAtomComplete(subscriptionId: Int): WithContext[T, U, Unit]
end OperatorSpec
