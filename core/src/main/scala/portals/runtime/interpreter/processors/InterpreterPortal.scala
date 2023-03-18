package portals.runtime.interpreter.processors

import scala.collection.mutable.ArrayDeque
import scala.util.Try

import portals.application.AtomicPortal
import portals.runtime.BatchedEvents.*
import portals.runtime.SuspendingRuntime.Completed
import portals.runtime.WrappedEvents.*
import portals.util.Key

/** Internal API. The TestPortal connects the askers with the replier. The
  * replier needs to be set dynamically by the replying workflow.
  */
private[portals] class InterpreterPortal(
    val portal: AtomicPortal[_, _],
    var replier: String = null,
    var replierTask: String = null
):
  // A single queue is used both for the TestAskBatch and TestRepBatch events.
  // This is sufficient, as the events have information with respect to the asker and replier.
  private val queue: ArrayDeque[EventBatch] = ArrayDeque.empty

  /** Set the key correctly if a key function exists. */
  private def setKeyAndMeta(ta: EventBatch): EventBatch =
    if !portal.key.isDefined then
      ta match
        case AskBatch(meta, list) =>
          AskBatch(
            meta.copy(replyingTask = Some(replierTask), replyingWF = Some(replier)),
            list
          )
        case _ => ta
    else
      ta match
        case AskBatch(meta, list) =>
          AskBatch(
            meta.copy(replyingTask = Some(replierTask), replyingWF = Some(replier)),
            list.map {
              case Ask(key, meta, event) =>
                portals.runtime.WrappedEvents.Ask(Key(portal.key.get(event.asInstanceOf)), meta, event)
              case x @ _ => x
            }
          )
        case ReplyBatch(meta, list) =>
          ReplyBatch(
            meta,
            list.map {
              case Reply(key, meta, event) =>
                portals.runtime.WrappedEvents.Reply(meta.askingKey, meta, event)
              case x @ _ => x
            }
          )
        case _ => ???

  /** Enqueue an atom. */
  def enqueue(tp: EventBatch) = tp match {
    case AskBatch(meta, list) =>
      queue.append(setKeyAndMeta(tp))
    case ReplyBatch(_, _) =>
      queue.append(setKeyAndMeta(tp))
    case _ => ??? // not allowed
  }

  def isEmpty: Boolean = queue.isEmpty

  def dequeue(): Option[EventBatch] = Some(queue.remove(0))
