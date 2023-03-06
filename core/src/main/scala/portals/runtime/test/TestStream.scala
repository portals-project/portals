package portals

import scala.collection.mutable.ArrayDeque

import portals.*
import portals.application.AtomicStream

class TestStream(stream: AtomicStream[_])(using rctx: TestRuntimeContext):
  private var atomQueue = ArrayDeque.empty[TestAtom]
  private var index: Long = -1

  /** Enqueue an atom to the atomic stream. */
  def enqueue(ta: TestAtom): Unit =
    atomQueue = atomQueue.append(ta)

  /** Read from the output atomic stream at the index idx. */
  def read(idx: Long): TestAtom =
    atomQueue((idx - index).toInt) // trust me :_)

  /** Returns the range of indexes that can be read. The range is inclusive,
    * i.e. [0, 0] means idx 0 can be read.
    */
  def getIdxRange(): (Long, Long) = (Math.max(0, index), index + atomQueue.size)

  /** Prune all atoms up to index idx. */
  def prune(idx: Long): Unit =
    atomQueue = atomQueue.drop((idx - index).toInt)
    index = idx
