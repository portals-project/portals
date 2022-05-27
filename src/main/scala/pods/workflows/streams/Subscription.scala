package pods.workflows

import java.util.concurrent.Flow.{Subscription => FSubscription}

/** Atomic Subscription */
trait Subscription extends FSubscription:
  override def request(x: Long): Unit
  
  override def cancel(): Unit

  // TODO: implement for fault-tolerance
  
  // /** Renew subscription starting from index idx */
  // // Use this for rollback recovery if the subscriber has failed, or a
  // // downstream subscriber has failed. Alternative: have the following in the 
  // // Publisher method: def subscribeFrom(s: Subscriber[_ >: O], idx: Long): Unit
  // def renew(idx: Long): Subscription

  // /** Report progress of the subscriber, used for pruning the log */
  // def reportProgress(idx: Long): Unit
