package portals

import java.util.LinkedList

import collection.JavaConverters._

trait SyncGenerator extends Executable {
  val g: AtomicGenerator[_]
}

class RuntimeGenerator[T](val g: AtomicGenerator[T]) extends SyncGenerator:
  private val logger = Logger(g.path)

  val eventBuffer = new LinkedList[WrappedEvent[T]]()
  val atomBuffer = new LinkedList[AtomSeq]()
  var subscribers = List[Recvable]()

  def subscribedBy(subscriber: Recvable) = subscribers ::= subscriber

  // we must read from generator to know if its events is enough to produce an atom
  override def isEmpty(): Boolean = {
    while (g.generator.hasNext() && atomBuffer.isEmpty) {
      val event = g.generator.generate()
      logger.debug("Generated event: " + event)
      event match {
        case portals.Generator.Event(key, e) => eventBuffer.add(Event(key, e))
        case portals.Generator.Atom =>
          eventBuffer.add(Atom())
          if (eventBuffer.size() > 1) {
            val atom = AtomSeq(eventBuffer.asScala.toList)
            atomBuffer.add(atom)
          }
          eventBuffer.clear()
        case portals.Generator.Error(_) => ???
        case _ =>
      }
    }

    atomBuffer.isEmpty
  }

  override def step(): Unit = subscribers.foreach(_.recv(g.stream, atomBuffer.poll()))
  override def stepAll(): Unit = while (!isEmpty()) step()
