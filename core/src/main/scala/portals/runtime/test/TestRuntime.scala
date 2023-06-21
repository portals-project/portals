package portals.runtime.test

import scala.util.Random

import portals.application.*
import portals.runtime.*
import portals.runtime.interpreter.*
import portals.runtime.BatchedEvents.*
import portals.runtime.SuspendingRuntime.*
import portals.util.Common.Types.*

private[portals] class TestRuntime(val seed: Option[Int] = None) extends SteppingRuntime:
  private val ctx: InterpreterContext = new InterpreterContext(seed)
  private val interpreter: Interpreter = new Interpreter(ctx)

  // TODO: make part of config
  private inline val GC_INTERVAL = 4

  /** The current step number of the execution. */
  private var _stepN: Long = 0
  private def stepN(): Long =
    _stepN += 1
    _stepN

  override def launch(application: Application): Unit =
    interpreter.launch(application)
  end launch

  override def canStep(): Boolean =
    interpreter.canStep()
  end canStep

  /** Recursively call resume until fully completed.
    *
    * This is guaranteed to terminate due to acyclic processing units. The
    * number of recursive steps is bounded by the number of `ShuffleTask`s.
    */
  private def recursiveResume(
      path: Path,
      shuffle: List[ShuffleBatch[_]],
      intermediate: List[EventBatch],
  ): Completed =
    interpreter.resume(path, shuffle) match
      case Completed(path, outputs) =>
        Completed(path, outputs ::: intermediate)
      case Suspended(path, shuffle, newIntermediate) =>
        recursiveResume(path, shuffle, newIntermediate ::: intermediate)
  end recursiveResume

  override def step(): Unit =
    interpreter.step() match
      case Completed(path, outputs) =>
        interpreter.feedAtoms(outputs)
      case Suspended(path, shuffle, outputs) =>
        recursiveResume(path, shuffle, outputs) match
          case Completed(path, outputs) =>
            interpreter.feedAtoms(outputs)

    if stepN() % GC_INTERVAL == 0 then interpreter.garbageCollect()
  end step

  override def shutdown(): Unit = () // do nothing
