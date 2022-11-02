package portals

import portals.system.async.AsyncLocalSystem
import portals.system.async.DataParallelSystem
import portals.system.async.MicroBatchingSystem
import portals.system.async.NoGuaranteesSystem
import portals.system.test.*

object Systems:
  def default(): TestSystem = new TestSystem()

  def test(): TestSystem = new TestSystem()

  def parallel(): System = new AsyncLocalSystem()

  def asyncLocalNoGuarantees(): System = new NoGuaranteesSystem()

  def asyncLocalMicroBatching(): System = new MicroBatchingSystem()

  def dataParallel(nPartitions: Int, nParallelism: Int): System =
    new DataParallelSystem(nPartitions, nParallelism)
