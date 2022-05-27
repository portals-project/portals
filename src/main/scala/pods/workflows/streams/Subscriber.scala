package pods.workflows

import java.util.concurrent.Flow.{Subscription => FSubscription}
import java.util.concurrent.Flow.{Subscriber => FSubscriber}

/** Atomic Subscriber */
trait Subscriber[T] extends FSubscriber[T]:
  override def onSubscribe(x: FSubscription): Unit

  override def onNext(x: T): Unit
  
  override def onError(x: Throwable): Unit
  
  override def onComplete(): Unit

  def onAtomComplete(id: Long): Unit // or onFuse() or onTick()

  // TODO: to be implemented for fault-tolerance 

  // /** Commit initiated with current progress with the current id */
  // def onPreCommit(id: Long): Unit

  // /** Commit has successfully completed */
  // // used to communicate that up to some atom has been committed, can be used 
  // // to either optimistically process events, or to wait until all events are 
  // // committed
  // def onCommitComplete(id: Long): Unit

  // /** Something failed, rollback to previous commit */
  // // used to communicate that the system is rolling back to a previous commit
  // def onRollback(id: Long): Unit
