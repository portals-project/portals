package portals.runtime.interpreter

import scala.util.Random

import portals.application.Application
import portals.runtime.interpreter.trackers.*

private[portals] class InterpreterContext(seed: Option[Int] = None):
  val rctx = new ApplicationTracker()
  val progressTracker = new ProgressTracker()
  val streamTracker = new StreamTracker()
  val graphTracker = new GraphTracker()
  val suspendingTracker = new SuspendingTracker()
  val rnd = seed.map(new Random(_)).getOrElse(new Random())
