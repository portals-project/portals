package portals.system.test

import scala.collection.mutable.Queue
import scala.util.Try

import portals.*

private[portals] class TestPortal(portal: AtomicPortal[_, _])(using TestRuntimeContext):
  private var receiver: String = null
  private val queue: Queue[TestAtom] = Queue.empty

  def setReceiver(r: String): Unit = receiver = r

  /** Enqueue an atom to the receiver. */
  def enqueue(tp: TestAtom) = tp match {
    case TestPortalAskBatch(portal, sender, _, list) =>
      queue.enqueue(TestPortalAskBatch(portal, sender, receiver, list))
    case TestPortalRepBatch(portal, sender, recvr, list) =>
      // TODO: consider renaming to asker and replyer, less ambiguous.
      queue.enqueue(tp)
    case _ => ??? // not allowed
  }

  def isEmpty: Boolean = queue.isEmpty

  def dequeue(): Option[TestAtom] = Try(queue.dequeue()).toOption
