package pods.atomic

/** XAtomic Reactive Streams 
 *  
 *  The XAtomic streams are exactly-once. They extend the Atomic streams with 
 *  exactly-once capabilities and transactions handling.
 * 
 *  XAtomic streams allow for optimistic execution and pessimistic execution.
 *  The XAtomicSubscriber can choose to execute atoms and events that are not 
 *  yet committed, in which case it must also be able to recover from an 
 *  upstream failure. It may also choose to only execute atoms that have been
 *  committed, in which case it does not have to consider upstream failures.
*/

trait XAtomicProcessor[T, S] extends XAtomicSubscriber[T] with XAtomicPublisher[S]
  // no methods, a processor is an alias for a subscriber and a publisher

trait XAtomicPublisher[T] extends AtomicPublisher[T]
  // no methods, but has to implement to send the correct event sequences to the subscribers

trait XAtomicSubscriber[T] extends AtomicSubscriber[T]:
  // commit initiated with current progress with the current id
  def onPreCommit(id: Long): Unit

  // commit has successfully completed
  // used to communicate that up to some atom has been committed
  // can be used to either optimistically process events, or to wait until all events are committed
  def onCommitComplete(id: Long): Unit

  // something failed, rollback to previous commit
  // used to communicate that the system is rolling back to a previous commit
  def onRollback(id: Long): Unit 

trait XAtomicSubscription extends AtomicSubscription:
  // renew subscription starting from index idx
  // use this for rollback recovery if the subscriber has failed, or a 
  // downstream subscriber has failed.
  // alternative: have the following in the XAtomicPublisher
  // method def  subscribeFrom(s: Subscriber[_ >: O], idx: Long): Unit 
  def renew(idx: Long): Subscription

  // report progress of the subscriber, used for pruning the log
  def reportProgress(idx: Long): Unit

