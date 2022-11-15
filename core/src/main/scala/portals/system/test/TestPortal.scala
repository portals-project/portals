package portals.system.test

import scala.collection.mutable.Queue
import scala.util.Try

import portals.*

private[portals] class TestPortal(portal: AtomicPortal[_, _])(using TestRuntimeContext):
  private var replier: String = null
  private val queue: Queue[TestAtom] = Queue.empty

  def setReceiver(r: String): Unit = replier = r

  /** Enqueue an atom. */
  def enqueue(tp: TestAtom) = tp match {
    case TestAskBatch(portal, asker, _, list) =>
      queue.enqueue(TestAskBatch(portal, asker, replier, list))
    case TestRepBatch(_, _, _, _) =>
      queue.enqueue(tp)
    case _ => ??? // not allowed
  }

  def isEmpty: Boolean = queue.isEmpty

  def dequeue(): Option[TestAtom] = Try(queue.dequeue()).toOption
