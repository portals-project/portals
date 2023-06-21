package portals.runtime

trait GarbageCollectingRuntime:
  /** Perform GC on the runtime objects. */
  def garbageCollect(): Unit
