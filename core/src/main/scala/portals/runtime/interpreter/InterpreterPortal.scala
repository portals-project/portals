package portals.runtime.interpreter

import scala.collection.mutable.ArrayDeque
import scala.util.Try

import portals.*
import portals.application.AtomicPortal
import portals.runtime.interpreter.InterpreterEvents.*
import portals.runtime.interpreter.InterpreterRuntimeContext
import portals.runtime.WrappedEvents.*

/** Internal API. The TestPortal connects the askers with the replier. The
  * replier needs to be set dynamically by the replying workflow.
  */
private[portals] class InterpreterPortal(
    portal: AtomicPortal[_, _],
    var replier: String = null,
    var replierTask: String = null
)(using InterpreterRuntimeContext):
  // A single queue is used both for the TestAskBatch and TestRepBatch events.
  // This is sufficient, as the events have information with respect to the asker and replier.
  private val queue: ArrayDeque[InterpreterAtom] = ArrayDeque.empty

  /** Set the key correctly if a key function exists. */
  private def setKey(ta: InterpreterAtom): InterpreterAtom =
    if !portal.key.isDefined then ta
    else
      ta match
        case InterpreterAskBatch(meta, list) =>
          InterpreterAskBatch(
            meta,
            list.map {
              case Ask(key, meta, event) =>
                portals.runtime.WrappedEvents.Ask(Key(portal.key.get(event.asInstanceOf)), meta, event)
              case x @ _ => x
            }
          )
        case InterpreterRepBatch(meta, list) =>
          InterpreterRepBatch(
            meta,
            list.map {
              case Reply(key, meta, event) =>
                portals.runtime.WrappedEvents.Reply(meta.askingKey, meta, event)
              case x @ _ => x
            }
          )
        case _ => ???

  /** Enqueue an atom. */
  def enqueue(tp: InterpreterAtom) = tp match {
    case InterpreterAskBatch(meta, list) =>
      queue.append(setKey(tp))
    case InterpreterRepBatch(_, _) =>
      queue.append(setKey(tp))
    case _ => ??? // not allowed
  }

  def isEmpty: Boolean = queue.isEmpty

  def dequeue(): Option[InterpreterAtom] = Some(queue.remove(0))
