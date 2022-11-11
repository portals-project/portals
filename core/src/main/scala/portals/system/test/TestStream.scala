package portals.system.test

import portals.*

class TestStream(stream: AtomicStream[_])(using rctx: TestRuntimeContext):
  private var atomQueue = List.empty[TestAtom]
  private var index: Long = -1

  /** Enqueue an atom to the atomic stream. */
  def enqueue(ta: TestAtom): Unit =
    atomQueue = atomQueue.appended(ta)

  /** Read from the output atomic stream at the index idx. */
  def read(idx: Long): TestAtom =
    atomQueue((idx - index).toInt) // trust me :_)

  // /** Read from an atomic stream for the provided output path, at the index idx. */
  // def read(path: String)(idx: Long): TestAtom = ???

  /** Returns the range of indexes that can be read.
    *
    * The range is inclusive, i.e. [0, 0] means that the idx 0 can be read.
    */
  def getIdxRange(): (Long, Long) = (Math.max(0, index), index + atomQueue.size)

  // /** Returns the range of indexes that can be read. */
  // override def getIdxRange(path: String): (Long, Long) = ???