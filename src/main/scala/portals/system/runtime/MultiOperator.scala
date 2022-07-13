package portals

import java.util.concurrent.Flow.Subscription
import java.util.concurrent.Flow.Subscriber
import java.util.concurrent.Flow.Publisher

trait MultiSubscriber[T]:
  def onNext(subscriptionId: Int, item: T): Unit
  def onComplete(subscriptionId: Int): Unit
  def onError(subscriptionId: Int, error: Throwable): Unit
  def onSubscribe(subscriptionId: Int, subscription: Subscription): Unit
  private[portals] def freshSubscriber(): Subscriber[T] // creates fresh subscriber

trait MultiPublisher[T]:
  def subscribe(msubscriber: MultiSubscriber[T]): Unit
  private[portals] def freshPublisher(): Publisher[T] // creates fresh publisher

trait MultiSubmitter[T]:
  def submit(item: T): Unit
  def seal(): Unit
  
trait MultiOperator[T, U] extends MultiSubscriber[T] with MultiPublisher[U] with MultiSubmitter[U]

/** MultiOperatorWithAtom extension of MultiOperator with WrappedEvents.
  *  
  * For convenience, using the MultiOperatorWithAtom[T, U] corresponds to the
  * implementation of using the MultiOperator on WrappedEvents of type T and U.
  * This makes it easier to use as the user does not have to worry about 
  * WrappedEvents.
 */
trait MultiOperatorWithAtom[T, U] extends MultiOperator[WrappedEvents[T], WrappedEvents[U]]
