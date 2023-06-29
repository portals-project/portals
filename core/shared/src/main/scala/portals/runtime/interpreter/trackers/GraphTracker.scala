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

/** Internal API. Tracks the graph which is spanned by all applications in
  * Portals.
  */
private[portals] class GraphTracker:
  /** Set of all pairs <from, to> edges. */
  private var _edges: Set[(String, String)] =
    Set.empty

  /** Add an edge <from, to> to the graph. */
  def addEdge(from: String, to: String): Unit =
    _edges += (from, to)

  /** Get all incoming edges to a graph node with the name 'path'. */
  def getInputs(path: String): Option[Set[String]] =
    Some(_edges.filter(_._2 == path).map(_._1))

  /** Get all outgoing edges to a graph node with the name 'path'. */
  def getOutputs(path: String): Option[Set[String]] =
    Some(_edges.filter(_._1 == path).map(_._2))
