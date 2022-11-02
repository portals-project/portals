package portals.system.test
import portals.*
trait TestStream:
  /** Enqueue an atom to the atomic stream. */
  def enqueue(ta: TestAtom): Unit

  /** Read from the output atomic stream at the index idx. */
  def read(idx: Long): TestAtom

  // /** Read from an atomic stream for the provided output path, at the index idx. */
  // def read(path: String)(idx: Long): TestAtom

  /** Perform GC, cleanup any left-over data that is no longer needed. */
  def cleanup(): Unit

  /** Returns the range of indexes that can be read. The range is inclusive, i.e. [0, 0] means that the idx 0 can be
    * read.
    */
  def getIdxRange(): (Long, Long)

  // /** Returns the range of indexes that can be read. */
  // def getIdxRange(path: String): (Long, Long)

case class TestAtomicStream(stream: AtomicStream[_])(using rctx: TestRuntimeContext) extends TestStream:
  var atomQueue = List.empty[TestAtom]
  var index: Long = -1

  override def enqueue(ta: TestAtom): Unit =
    atomQueue = atomQueue.appended(ta)

  override def read(idx: Long): TestAtom =
    atomQueue((idx - index).toInt) // trust me :_)

  // override def read(path: String)(idx: Long): TestAtom = ???

  override def cleanup(): Unit = () // do nothing for now

  override def getIdxRange(): (Long, Long) = (Math.max(0, index), index + atomQueue.size)

  // override def getIdxRange(path: String): (Long, Long) = ???
