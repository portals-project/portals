package pods.atomic

/** Atomic Reactive Streams
 * 
 *  The Atomic streams are atomic, that is they transport atoms.
 * 
 *  They extend from reactive streams by additionally transporting atoms.
 */
trait AtomicProcessor[T, S] extends Processor[T, S]

trait AtomicPublisher[T] extends Publisher[T]

trait AtomicSubscriber[T] extends Subscriber[T]:
  def onAtomComplete(id: Long): Unit // or onFuse() or onTick()

trait AtomicSubscription extends Subscription
