package portals

import portals.system.parallel.*
import portals.system.test.*

trait Systems

object Systems extends Systems:
  def default(): PortalsSystem = test()

  def test(): TestSystem = new TestSystem()

  def parallel(): PortalsSystem = new ParallelSystem()
