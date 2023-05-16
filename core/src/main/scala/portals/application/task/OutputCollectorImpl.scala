package portals.application.task

import scala.collection.mutable.ArrayDeque

import portals.runtime.WrappedEvents.*
import portals.util.Future
import portals.util.Key
import portals.util.Logger

/** Internal API. OutputCollector to collect submitted events as side effects.
  * Works for all kinds of tasks.
  */
private[portals] class OutputCollectorImpl[T, U, Req, Rep] extends OutputCollector[T, U, Req, Rep]:
  //////////////////////////////////////////////////////////////////////////////
  // Task
  //////////////////////////////////////////////////////////////////////////////
  private var _output = ArrayDeque[WrappedEvent[U]]()
  override def submit(event: WrappedEvent[U]): Unit = _output.addOne(event)
  def getOutput(): List[WrappedEvent[U]] = _output.toList
  def removeAll(): Seq[WrappedEvent[U]] = _output.removeAll() // clears and returns
  def clear(): Unit = _output.clear()

  //////////////////////////////////////////////////////////////////////////////
  // Asker Task
  //////////////////////////////////////////////////////////////////////////////
  private var _asks = ArrayDeque[Ask[Req]]()
  override def ask(
      portal: String,
      askingTask: String,
      req: Req,
      key: Key, // TODO: this is confusing exactly which key is meant here, askingKey or replyingKey
      id: Int,
      askingWF: String,
  ): Unit =
    _asks.addOne(Ask(key, PortalMeta(portal, askingTask, key, id, askingWF), req))
  def getAskOutput(): List[Ask[Req]] = _asks.toList
  def removeAllAsks(): Seq[Ask[Req]] = _asks.removeAll() // clears and returns
  def clearAsks(): Unit = _asks.clear()

  //////////////////////////////////////////////////////////////////////////////
  // Replier Task
  //////////////////////////////////////////////////////////////////////////////
  private var _reps = ArrayDeque[Reply[Rep]]()
  override def reply(
      r: Rep,
      portal: String,
      askingTask: String,
      key: Key, // TODO: this is confusing exactly which key is meant here, askingKey or replyingKey
      id: Int,
      askingWF: String,
  ): Unit = _reps.addOne(Reply(key, PortalMeta(portal, askingTask, key, id, askingWF), r))
  def getRepOutput(): List[Reply[Rep]] = _reps.toList
  def removeAllReps(): Seq[Reply[Rep]] = _reps.removeAll() // clears and returns
  def clearReps(): Unit = _reps.clear()
end OutputCollectorImpl // class
