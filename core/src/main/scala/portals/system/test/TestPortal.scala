package portals.system.test

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

  /** Enqueue an atom. */
  def enqueue(tp: TestAtom) = tp match {
    case TestAskBatch(meta, list) =>
      queue.append(tp)
    // Here we set the replier to the replier, as this information is not known to the asking workflow.
    // queue.append(
    //   TestAskBatch(
    //     portal,
    //     asker,
    //     replier,
    //     list.map(x => {
    //       val y = x.asInstanceOf[Ask[_]]; Ask(y.key, portal, asker, replierTask, y.asker, y.id, y.event)
    //     })
    //   )
    // )
    case TestRepBatch(_, _) =>
      queue.append(tp)
    case _ => ??? // not allowed
  }

  def isEmpty: Boolean = queue.isEmpty

  def dequeue(): Option[TestAtom] = Some(queue.remove(0))
