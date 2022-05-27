package pods.workflows

/** Atomic Processor */
trait Processor[T, S] extends Publisher[T] with Subscriber[S]
  // no methods, a processor is an alias for a subscriber and a publisher
