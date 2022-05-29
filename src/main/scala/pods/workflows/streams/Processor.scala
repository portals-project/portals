package pods.workflows

/** Atomic Processor */
trait Processor[I, O] extends Subscriber[I] with  Publisher[O]
  // no methods, a processor is an alias for a subscriber and a publisher
