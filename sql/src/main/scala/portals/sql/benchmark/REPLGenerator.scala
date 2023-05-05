package portals.sql.benchmark

import portals.application.generator.Generator
import portals.runtime.WrappedEvents
import portals.runtime.WrappedEvents.WrappedEvent
import portals.util.Key

import java.util.concurrent.LinkedBlockingQueue

class REPLGenerator extends Generator[String]:
  val _iter = new REPL().iterator

  override def generate(): WrappedEvent[String] =
    val nxt = _iter.next()
    WrappedEvents.Event(Key(nxt.hashCode()), nxt)

  override def hasNext(): Boolean = _iter.hasNext

  def add(q: String): Unit = _iter.enqueue(q)

class REPL:

  class BlockingQueueIterator[T] extends Iterator[T] {
    val queue = new LinkedBlockingQueue[T]()

    override def hasNext: Boolean = !queue.isEmpty

    override def next(): T = queue.take()

    def enqueue(e: T) = queue.offer(e)
  }

  val iterator = new BlockingQueueIterator[String]()
  val waitForMsg = new LinkedBlockingQueue[Int]()
  val waitForProcessing = new LinkedBlockingQueue[Int]()
  var requestCnt = 0
  var prefix = ""

  def setPrefix(s: String): Unit = prefix = s

