package portals

import portals.system.async.AsyncLocalSystem
import portals.system.async.DataParallelSystem
import portals.system.async.MicroBatchingSystem
import portals.system.async.NoGuaranteesSystem
import portals.system.test.*

object Systems:
  def default(): TestSystem = new TestSystem()

  def syncLocal(): LocalSystemContext = new SyncLocalSystem()

  // // for testing async system instead of local
  // def syncLocal(): LocalSystemContext = new LocalSystemContext {
  //   private val system = AsyncLocalSystem()
  //   def launch(application: Application): Unit = system.launch(application)
  //   def shutdown(): Unit = system.shutdown()
  //   def isEmpty(): Boolean = false
  //   def step(): Unit = Thread.sleep(100)
  //   def stepAll(): Unit = Thread.sleep(1000)
  // }

  // // // for testing microbatching system instead of local
  // def syncLocal(): LocalSystemContext = new LocalSystemContext {
  //   private val system = MicroBatchingSystem()
  //   def launch(application: Application): Unit = system.launch(application)
  //   def shutdown(): Unit = system.shutdown()
  //   def isEmpty(): Boolean = false
  //   def step(): Unit = Thread.sleep(100)
  //   def stepAll(): Unit = Thread.sleep(1000)
  // }

  def asyncLocal(): SystemContext = new AsyncLocalSystem()

  def asyncLocalNoGuarantees(): SystemContext = new NoGuaranteesSystem()

  def asyncLocalMicroBatching(): SystemContext = new MicroBatchingSystem()

  def dataParallel(nPartitions: Int, nParallelism: Int): SystemContext =
    new DataParallelSystem(nPartitions, nParallelism)
