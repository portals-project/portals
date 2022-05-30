package pods.workflows

/** Atomic Submitter */
trait Submitter[T]:
  private[pods] def submit(event: T): Unit

  private[pods] def seal(): Unit
  
  private[pods] def fuse(): Unit

  // // TODO: implement for fault-tolerance

  // private[pods] def precommit(): Long // returns commit id

  // private[pods] def isCommitted(commit_id: Long): Boolean // check if commit was executed
  
  // private[pods] def commit(): Unit // block until commit is completed
  
  // private[pods] def commitAsync(): Unit // commit async
  
  // private[pods] def rollback(): Unit // trigger rollback
