package pods.workflows

import java.util.concurrent.Flow.Subscription

trait OperatorSpec[T, U]: 
  type WithContext[S] = OperatorCtx[T, U] ?=> S
  final def submit(item: U): WithContext[Unit] = summon[OperatorCtx[T, U]].submit(item)
  final def fuse()         : WithContext[Unit] = summon[OperatorCtx[T, U]].fuse()
  final def seal()         : WithContext[Unit] = summon[OperatorCtx[T, U]].seal()
  
  def onNext(subscriptionId: Int, item: T): WithContext[Unit]
  def onComplete(subscriptionId: Int): WithContext[Unit]
  def onError(subscriptionId: Int, error: Throwable): WithContext[Unit]
  def onSubscribe(subscriptionId: Int, subscription: Subscription): WithContext[Unit]
  def onAtomComplete(subscriptionId: Int): WithContext[Unit]
end OperatorSpec
