package portals

import scala.collection.mutable.ArrayDeque
import scala.util.Try

import portals.*

/** Internal API. The TestPortal connects the askers with the replier. The replier needs to be set dynamically by the
  * replying workflow.
  */
private[portals] class TestPortal(
    portal: AtomicPortal[_, _],
    var replier: String = null,
    var replierTask: String = null
)(using TestRuntimeContext):
  // A single queue is used both for the TestAskBatch and TestRepBatch events.
  // This is sufficient, as the events have information with respect to the asker and replier.
  private val queue: ArrayDeque[TestAtom] = ArrayDeque.empty

  /** Set the key correctly if a key function exists. */
  private def setKey(ta: TestAtom): TestAtom =
    if !portal.key.isDefined then ta
    else
      ta match
        case TestAskBatch(meta, list) =>
          TestAskBatch(
            meta,
            list.map {
              case Ask(key, meta, event) =>
                portals.Ask(Key(portal.key.get(event.asInstanceOf)), meta, event)
              case x @ _ => x
            }
          )
        case TestRepBatch(meta, list) =>
          TestRepBatch(
            meta,
            list.map {
              case Reply(key, meta, event) =>
                portals.Reply(meta.askingKey, meta, event)
              case x @ _ => x
            }
          )
        case _ => ???

  /** Enqueue an atom. */
  def enqueue(tp: TestAtom) = tp match {
    case TestAskBatch(meta, list) =>
      queue.append(setKey(tp))
    case TestRepBatch(_, _) =>
      queue.append(setKey(tp))
    case _ => ??? // not allowed
  }

  def isEmpty: Boolean = queue.isEmpty

  def dequeue(): Option[TestAtom] = Some(queue.remove(0))
