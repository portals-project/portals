package portals

import portals.system.async.AsyncLocalSystem
import portals.system.async.AsyncLocalSystemWithoutAlignment
import portals.system.async.DataParallelSystem

object Systems:
  def syncLocal(): LocalSystemContext = new SyncLocalSystem()

  // // for testing async system instead of local
  // def syncLocal(): LocalSystemContext = new LocalSystemContext {
  //   private val system = AsyncLocalSystem()
  //   def launch(application: Application): Unit = system.launch(application)
  //   def shutdown(): Unit = system.shutdown()
  //   val registry: GlobalRegistry = null
  //   def isEmpty(): Boolean = false
  //   def step(): Unit = Thread.sleep(100)
  //   def stepAll(): Unit = Thread.sleep(1000)
  // }

  def asyncLocal(): SystemContext = new AsyncLocalSystem()

  // TODO: replace with some other way of doing this :p
  def asyncLocalWOA(): SystemContext = new AsyncLocalSystemWithoutAlignment()

  def dataParallel(nPartitions: Int): SystemContext = new DataParallelSystem(nPartitions)
