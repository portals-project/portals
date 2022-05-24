package pods.atomic

/** Exclusive output reference of a stream (publisher)
 * 
 *  Single-owner
 */
trait ORef[T]:
  def subscribe(subscriber: SIRef[T]): Unit
  def close(): Unit

/** Exclusive input reference of a stream (subscriber)
 *  
 *  Single-owner
*/
trait IRef[T]:
  def submit(event: T): Unit // submit event
  def seal(): Unit // or close()
  def fuse(): Unit // or tick()

/** Shared input reference of a stream
 *  
 *  No single owner, no methods
*/
trait SIRef[T] extends Serializable

/** Shared output reference of a stream 
 *  
 *  No single owner
*/
trait SORef[T] extends Serializable:
  /** Setup a subscription between the SORef and the SIRef */
  def subscribe(subscriber: SIRef[T]): Unit

// Similarly to the Atomic streams, we also have references for the XAtomic streams
trait XORef[T] extends ORef[T]
trait XIRef[T] extends IRef[T]:
  def precommit(): Long // returns commit id
  def isCommitted(commit_id: Long): Boolean // check if commit was executed
  def commit(): Unit // block until commit is completed
  def commitAsync(): Unit // commit async
  def rollback(): Unit // trigger rollback
trait XSIRef[T] extends SIRef[T]
trait XSORef[T] extends SORef[T]
