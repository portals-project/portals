package portals

import scala.annotation.experimental

/** Generator. */
trait Generator[T]:
  /** Generates events for the atomic stream. */
  def generate(): WrappedEvent[T]

  /** If the generator can produce more events. */
  def hasNext(): Boolean
end Generator

/** Generator implementations */
private[portals] object GeneratorImpls:
  /** FromIteratorOfIterators */
  case class FromIteratorOfIterators[T](
      itit: Iterator[Iterator[T]],
      keys: Iterator[Iterator[Key[Long]]],
  ) extends Generator[T]:
    def generate(): WrappedEvent[T] = _iter.next()

    def hasNext(): Boolean = _iter.hasNext

    private val _iter: Iterator[WrappedEvent[T]] =
      itit
        .zip(keys)
        .map { x =>
          x._1
            .zip(x._2)
            .map { case (x, key) => Event[T](key, x) }
            .concat(Iterator.single(Atom))
        }
        .concat(Iterator.single(Iterator.single(Seal)))
        .flatten
  end FromIteratorOfIterators // case class

  /** FromIteratorOfIteratorsWithKeyExtractor */
  case class FromIteratorOfIteratorsWithKeyExtractor[T](
      itit: Iterator[Iterator[T]],
      keyExtractor: T => Key[Long] = (x: T) => Key(x.hashCode()),
  ) extends Generator[T]:
    def generate(): WrappedEvent[T] = _iter.next()

    def hasNext(): Boolean = _iter.hasNext

    private val _iter: Iterator[WrappedEvent[T]] =
      itit
        .map {
          _.map { x =>
            Event[T](keyExtractor(x), x)
          }.concat(Iterator.single(Atom))
        }
        .concat(Iterator.single(Iterator.single(Seal)))
        .flatten
  end FromIteratorOfIteratorsWithKeyExtractor // case class

  /** External Ref */
  @experimental
  @deprecated
  class ExternalRef[T]():
    def submit(t: T): Unit = cb(Event(Key(t.hashCode()), t))
    def submit(t: T, key: Key[Long]): Unit = cb(Event(key, t))
    def error(t: Throwable): Unit = cb(Error(t))
    def fuse(): Unit = cb(Atom)
    def seal(): Unit = cb(Seal)
    private[portals] var cb: WrappedEvent[T] => Unit = _

  /** External */
  @experimental
  @deprecated
  class External[T](ref: ExternalRef[T]) extends Generator[T]:
    // need lock for thread support
    import java.util.concurrent.locks.ReentrantLock

    private var formingAtom: Seq[WrappedEvent[T]] = Seq.empty
    private var events: Seq[WrappedEvent[T]] = Seq.empty

    private val lock = ReentrantLock()

    val cb = (ge: WrappedEvent[T]) =>
      lock.lock()
      ge match
        case Event(key, t) =>
          formingAtom = formingAtom :+ Event(key, t)
        case Error(t) =>
          formingAtom = formingAtom :+ Error(t)
        case Atom =>
          formingAtom = formingAtom :+ Atom
          events = events ++ formingAtom
          formingAtom = Seq.empty
        case Seal =>
          formingAtom = formingAtom :+ Seal
          events = events ++ formingAtom
          formingAtom = Seq.empty
        case _ => ??? // shouldn't happen
      lock.unlock()
    end cb

    ref.cb = cb

    def generate(): WrappedEvent[T] =
      lock.lock()
      events match
        case head :: tail =>
          events = tail
          lock.unlock()
          head
        case _ => // shouldn't happen
          lock.unlock()
          ???

    def hasNext(): Boolean = !events.isEmpty
  end External // case class

/** Generator factories. */
object Generators:
  import GeneratorImpls.*

  def fromIterator[T](it: Iterator[T]): Generator[T] =
    fromIteratorOfIterators(Iterator.single(it))

  def fromIterator[T](it: Iterator[T], keys: Iterator[Key[Long]]): Generator[T] =
    fromIteratorOfIterators(Iterator.single(it), Iterator.single(keys))

  def fromIteratorOfIterators[T](itit: Iterator[Iterator[T]]): Generator[T] =
    FromIteratorOfIteratorsWithKeyExtractor(itit)

  def fromIteratorOfIterators[T](itit: Iterator[Iterator[T]], keys: Iterator[Iterator[Key[Long]]]): Generator[T] =
    FromIteratorOfIterators(itit, keys)

  def fromRange(start: Int, end: Int, step: Int): Generator[Int] =
    fromIteratorOfIterators(Iterator.range(start, end).grouped(step).map(_.iterator))

  @experimental
  @deprecated
  def external[T](): (ExternalRef[T], Generator[T]) =
    val extRef = ExternalRef[T]()
    val generator = External[T](extRef)
    (extRef, generator)
