package portals.runtime.interpreter.processors

import scala.collection.mutable.ArrayDeque

import portals.application.AtomicStream
import portals.runtime.BatchedEvents.*

private[portals] class InterpreterStream(stream: AtomicStream[_]):
  private var atomQueue = ArrayDeque.empty[EventBatch]
  private var index: Long = -1

  /** Enqueue an atom to the atomic stream. */
  def enqueue(ta: EventBatch): Unit =
    atomQueue = atomQueue.append(ta)

  /** Read from the output atomic stream at the index idx. */
  def read(idx: Long): EventBatch =
    atomQueue((idx - index).toInt) // trust me :_)

  /** Returns the range of indexes that can be read. The range is inclusive,
    * i.e. [0, 0] means idx 0 can be read.
    */
  def getIdxRange(): (Long, Long) = (Math.max(0, index), index + atomQueue.size)

  /** Prune all atoms up to index idx. */
  def prune(idx: Long): Unit =
    atomQueue = atomQueue.drop((idx - index).toInt)
    index = idx
