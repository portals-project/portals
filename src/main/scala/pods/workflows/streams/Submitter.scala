package pods.workflows

/** Atomic Submitter */
trait Submitter[T]:
  private[pods] def submit(event: T): Unit

  private[pods] def seal(): Unit
  
  private[pods] def fuse(): Unit
