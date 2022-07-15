package portals

/** IStreamRef */
trait IStreamRef[T]:
  private[portals] def submit(event: T): Unit
  
  private[portals] def fuse(): Unit // or tick()

  // // TODO: implement for fault-tolerance
  // def precommit(): Long // returns commit id

  // def isCommitted(commit_id: Long): Boolean // check if commit was executed
  
  // def commit(): Unit // block until commit is completed
  
  // def commitAsync(): Unit // commit async
  
  // def rollback(): Unit // trigger rollback
