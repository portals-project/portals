package portals.system

import portals.application.Application

trait Systems

import portals.system.PortalsSystem
import portals.system.TestSystem

object Systems extends Systems:
  def default(): PortalsSystem = test()

  def test(): TestSystem = new TestSystem()

  def test(seed: Int): TestSystem = new TestSystem(Some(seed))

  def parallel(nThreads: Int): PortalsSystem = new ParallelSystem(nThreads)
