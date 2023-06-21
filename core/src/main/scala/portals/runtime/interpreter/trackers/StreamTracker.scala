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

/** Internal API. Tracks all streams of all applications.
  *
  * The stream tracker is used to track the progress of the streams, i.e. what
  * range of indices of the stream that can be read. The smallest index may be
  * incremented due to garbage collection over time.
  */
private[portals] class StreamTracker:
  /** Maps the progress of a path (String) to a pair [from, to], the range is
    * inclusive and means that all indices starting from 'from' until
    * (including) 'to' can be read.
    */
  private var _progress: Map[String, (Long, Long)] =
    Map.empty

  /** Initialize a new stream by settings its progress to <0, -1>, that is it is
    * empty for now.
    */
  def initStream(stream: String): Unit =
    _progress += stream -> (0, -1)

  /** Set the progress of a stream to <from, to>, for which the range is
    * inclusive. Use this with care, use incrementProgress instead where
    * possible.
    */
  def setProgress(stream: String, from: Long, to: Long): Unit =
    _progress += stream -> (from, to)

  /** Increments the progress of a stream by 1. */
  def incrementProgress(stream: String): Unit =
    _progress += stream -> (_progress(stream)._1, _progress(stream)._2 + 1)

  /** Returns the progress of a stream as an optional range <From, To>, for
    * which the range is inclusive.
    */
  def getProgress(stream: String): Option[(Long, Long)] =
    _progress.get(stream)
