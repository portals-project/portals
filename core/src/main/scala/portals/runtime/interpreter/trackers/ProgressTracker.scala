package portals.runtime.interpreter.trackers

import scala.util.Random

import portals.application.*
import portals.application.task.AskerReplierTask
import portals.application.task.AskerTask
import portals.application.task.ReplierTask
import portals.compiler.phases.RuntimeCompilerPhases
import portals.runtime.BatchedEvents.*
import portals.runtime.PortalsRuntime
import portals.runtime.WrappedEvents.*

/** Internal API. Tracks the progress for a path with respect to other streams.
  */
private[portals] class ProgressTracker:
  // progress tracker for each Path;
  // for a Path (String) this gives the progress w.r.t. all input dependencies (Map[String, Long])
  private var progress: Map[String, Map[String, Long]] = Map.empty

  /** Set the progress of path and dependency to index. */
  def setProgress(path: String, dependency: String, idx: Long): Unit =
    progress +=
      path ->
        (progress(path) + (dependency -> idx))

  /** Increments the progress of path w.r.t. dependency by 1. */
  def incrementProgress(path: String, dependency: String): Unit =
    progress +=
      path ->
        (progress(path) + (dependency -> (progress(path)(dependency) + 1)))

  /** Initialize the progress tracker for a certain path and dependency to -1.
    */
  def initProgress(path: String, dependency: String): Unit =
    progress += path -> (progress.getOrElse(path, Map.empty) + (dependency -> -1L))

  /** Get the current progress of the path and dependency. */
  def getProgress(path: String, dependency: String): Option[Long] =
    progress.get(path).flatMap(deps => deps.get(dependency))

  /** Get the current progress of the path. */
  def getProgress(path: String): Option[Map[String, Long]] =
    progress.get(path)
