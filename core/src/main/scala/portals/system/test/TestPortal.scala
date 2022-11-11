package portals.system.test

import portals.*

private[portals] class TestPortal(portal: AtomicPortal[_, _])(using TestRuntimeContext):

  private var tstreams: Map[String, TestStream] = Map.empty

  /** Initialize portal for some name. */
  def init(path: String): Unit = tstreams += path -> TestStream(null) // FIXME: null :(

  /** Enqueue an atom to the receiver. */
  def enqueue(tp: TestPortalAskBatch[_]) = tp match {
    case TestPortalAskBatch(sendr, recvr, list) =>
      tstreams(recvr).enqueue(tp)
  }

  /** Read from an atomic stream for the provided output path, at the index idx. */
  def read(path: String)(idx: Long): TestAtom = tstreams(path).read(idx)

  /** Returns the range of indexes that can be read for a path.
    *
    * The range is inclusive, i.e. [0, 0] means that the idx 0 can be read.
    */
  def getIdxRange(path: String): (Long, Long) = tstreams(path).getIdxRange()
