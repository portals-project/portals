package portals

import scala.annotation.targetName

/** Events to be produced by the generator. */
object Generator:
  sealed trait GeneratorEvent[+T]
  case class Event[T](key: Key[Int], t: T) extends GeneratorEvent[T]
  case class Error[T](t: Throwable) extends GeneratorEvent[T]
  case object Atom extends GeneratorEvent[Nothing]
  case object Seal extends GeneratorEvent[Nothing]

/** Generator. */
trait Generator[T]:
  import Generator.*

  /** Generates events for the atomic stream. */
  def generate(): GeneratorEvent[T]

  /** If the generator can produce more events. */
  def hasNext(): Boolean
end Generator

/** Generator implementations */
private[portals] object GeneratorImpls:
  import Generator.*

  /** FromIteratorOfIterators */
  case class FromIteratorOfIterators[T](
      itit: Iterator[Iterator[T]],
      keys: Iterator[Iterator[Key[Int]]],
  ) extends Generator[T]:
    def generate(): GeneratorEvent[T] = _iter.next()

    def hasNext(): Boolean = _iter.hasNext

    private val _iter: Iterator[GeneratorEvent[T]] =
      itit
        .zip(keys)
        .map { x => x._1.zip(x._2).map { case (x, key) => Event[T](key, x) }.concat(Iterator.single(Atom)) }
        .concat(Iterator.single(Iterator.single(Seal)))
        .flatten
  end FromIteratorOfIterators // case class

  /** FromIteratorOfIteratorsWithKeyExtractor */
  case class FromIteratorOfIteratorsWithKeyExtractor[T](
      itit: Iterator[Iterator[T]],
      keyExtractor: T => Key[Int] = (x: T) => Key(x.hashCode()),
  ) extends Generator[T]:
    private val _iter: Iterator[GeneratorEvent[T]] = itit
      .map {
        _.map { x => Event[T](keyExtractor(x), x) }
          .concat(Iterator.single(Atom))
      }
      .concat(Iterator.single(Iterator.single(Seal)))
      .flatten

    def generate(): GeneratorEvent[T] = _iter.next()

    def hasNext(): Boolean = _iter.hasNext
  end FromIteratorOfIteratorsWithKeyExtractor // case class

  /** External Ref */
  class ExternalRef[T]():
    def submit(t: T): Unit = cb(Event(Key(t.hashCode()), t))
    def submit(t: T, key: Key[Int]): Unit = cb(Event(key, t))
    def error(t: Throwable): Unit = cb(Error(t))
    def fuse(): Unit = cb(Atom)
    def seal(): Unit = cb(Seal)
    private[portals] var cb: GeneratorEvent[T] => Unit = _

  /** External */
  case class External[T](ref: ExternalRef[T]) extends Generator[T]:
    private var formingAtom: Seq[GeneratorEvent[T]] = Seq.empty
    private var events: Seq[GeneratorEvent[T]] = Seq.empty

    val cb = (ge: GeneratorEvent[T]) =>
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
    end cb

    ref.cb = cb

    def generate(): GeneratorEvent[T] = events match
      case head :: tail =>
        events = tail
        head

    def hasNext(): Boolean = !events.isEmpty
  end External // case class

/** Generator factories. */
object Generators:
  import Generator.*
  import GeneratorImpls.*

  def fromIterator[T](it: Iterator[T]): Generator[T] =
    fromIteratorOfIterators(Iterator.single(it))

  def fromIterator[T](it: Iterator[T], keys: Iterator[Key[Int]]): Generator[T] =
    fromIteratorOfIterators(Iterator.single(it), Iterator.single(keys))

  def fromIteratorOfIterators[T](itit: Iterator[Iterator[T]]): Generator[T] =
    FromIteratorOfIteratorsWithKeyExtractor(itit)

  def fromIteratorOfIterators[T](itit: Iterator[Iterator[T]], keys: Iterator[Iterator[Key[Int]]]): Generator[T] =
    FromIteratorOfIterators(itit, keys)

  def fromRange(start: Int, end: Int, step: Int): Generator[Int] =
    fromIteratorOfIterators(Iterator.range(start, end).grouped(step).map(_.iterator))

  def external[T](): (ExternalRef[T], Generator[T]) =
    val extRef = ExternalRef[T]()
    val generator = External[T](extRef)
    (extRef, generator)
