package portals.sql.benchmark

import java.util.concurrent.LinkedBlockingQueue

import portals.application.generator.Generator
import portals.runtime.WrappedEvents
import portals.runtime.WrappedEvents.WrappedEvent
import portals.util.Key

class REPLGeneratorAtom[T] extends Generator[T]:
  val _iter = new REPL[T]().iterator
  var atom_next = false

  override def generate(): WrappedEvent[T] =
    if atom_next then
      atom_next = false
      WrappedEvents.Atom
    else
      val nxt = _iter.next()
      atom_next = true
      WrappedEvents.Event(Key(nxt.hashCode()), nxt)

  override def hasNext(): Boolean = atom_next || _iter.hasNext

  def add(q: T): Unit = _iter.enqueue(q)

class REPLGenerator[T] extends Generator[T]:
  val _iter = new REPL[T]().iterator

  override def generate(): WrappedEvent[T] =
    val nxt = _iter.next()
    WrappedEvents.Event(Key(nxt.hashCode()), nxt)

  override def hasNext(): Boolean = _iter.hasNext

  def add(q: T): Unit = _iter.enqueue(q)

class REPL[R]:

  class BlockingQueueIterator[T] extends Iterator[T] {
    val queue = new LinkedBlockingQueue[T]()

    override def hasNext: Boolean = !queue.isEmpty

    override def next(): T = queue.take()

    def enqueue(e: T) = queue.offer(e)
  }

  val iterator = new BlockingQueueIterator[R]()
