package portals.benchmark.systems

import portals.PortalsSystem
import portals.Systems

extension (systems: Systems) {
  def asyncLocalNoGuarantees(): PortalsSystem = new NoGuaranteesSystem()

  def asyncLocalMicroBatching(): PortalsSystem = new MicroBatchingSystem()

  def dataParallel(nPartitions: Int, nParallelism: Int): PortalsSystem =
    new DataParallelSystem(nPartitions, nParallelism)
}
