package portals.runtime.executor

import scala.collection.immutable.VectorBuilder
import scala.collection.mutable.Map

import portals.application.splitter.Splitter
import portals.runtime.WrappedEvents.*

/** Internal API. Executor for Generators. */
private[portals] class SplitterExecutorImpl[T]:
  // setup
  private var path: String = _
  private var splitter: Splitter[T] = _

  /** Setup the `splitter` for `path`. */
  def setup(path: String, splitter: Splitter[T]): Unit =
    this.path = path
    this.splitter = splitter

  def addOutput(path: String, filter: T => Boolean): Unit =
    splitter.addOutput(path, filter)

  def removeOutput(path: String): Unit =
    splitter.removeOutput(path)

  def splitEvent(event: WrappedEvent[T]): (String, WrappedEvent[T]) =
    val r = splitter.split(List(event))
    (r.head._1, r.head._2.head)

  def split(atom: List[WrappedEvent[T]]): List[(String, WrappedEvent[T])] =
    val r = splitter.split(atom)
    r.map(x => (x._1, x._2.head))
