package portals.distributed.remote

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

import scala.concurrent.Future

import portals.application.*
import portals.application.Application
import portals.application.AtomicStreamRefKind
import portals.distributed.remote.RemoteShared.*
import portals.distributed.ApplicationLoader
import portals.distributed.ApplicationLoader.PortalsClassLoader
import portals.distributed.Events.*
import portals.runtime.interpreter.*
import portals.runtime.test.TestRuntime
import portals.runtime.BatchedEvents.*
import portals.runtime.PortalsRuntime
import portals.runtime.SuspendingRuntime.*
import portals.system.Systems
import portals.util.Common.Types.Path

import upickle.default.*
import upickle.default.read

class RemoteRuntime extends PortalsRuntime:
  private val ctx: InterpreterContext = new InterpreterContext()
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

  def canStep(): Boolean =
    interpreter.canStep()
  end canStep

  /** Recursively call resume until fully completed.
    *
    * This is guaranteed to terminate due to acyclic processing units. The
    * number of recursive steps is bounded by the number of `ShuffleTask`s.
    */
  private[portals] def recursiveResume(
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

  def feed(l: List[EventBatch]): Unit = interpreter.feedAtoms(l)

  def maybeGC(): Unit =
    if stepN() % GC_INTERVAL == 0 then interpreter.garbageCollect()

  def step(): List[EventBatch] =
    interpreter.step() match
      case Completed(path, outputs) =>
        val (remote, local) = outputs.partition(batch => REMOTEFILTER(batch))
        interpreter.feedAtoms(local)
        maybeGC()
        remote
      case Suspended(path, shuffle, outputs) =>
        recursiveResume(path, shuffle, outputs) match
          case Completed(path, outputs) =>
            val (remote, local) = outputs.partition(batch => REMOTEFILTER(batch))
            interpreter.feedAtoms(local)
            maybeGC()
            remote
  end step

  override def shutdown(): Unit = () // do nothing
