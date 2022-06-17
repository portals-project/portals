package pods.workflows

import java.util.concurrent.Flow.Subscription
import java.util.concurrent.Flow.Subscriber
import java.util.concurrent.Flow.Publisher

trait MultiSubscriber[T]:
  def onNext(subscriptionId: Int, item: T): Unit
  def onComplete(subscriptionId: Int): Unit
  def onError(subscriptionId: Int, error: Throwable): Unit
  def onSubscribe(subscriptionId: Int, subscription: Subscription): Unit
  private[pods] def freshSubscriber(): Subscriber[T] // creates fresh subscriber

trait MultiPublisher[T]:
  def subscribe(msubscriber: MultiSubscriber[T]): Unit
  private[pods] def freshPublisher(): Publisher[T] // creates fresh publisher

trait MultiSubmitter[T]:
  def submit(item: T): Unit
  def seal(): Unit
  
trait MultiOperator[T, U] extends MultiSubscriber[T] with MultiPublisher[U] with MultiSubmitter[U]

trait MultiOperatorWithAtom[T, U] extends MultiOperator[WrappedEvents[T], WrappedEvents[U]]
