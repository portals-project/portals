package portals

import scala.annotation.targetName

/** Events to be produced by the generator. */
object Generator:
  sealed trait GeneratorEvent[+T]
  case class Event[T](key: Key[Int], value: T) extends GeneratorEvent[T]
  case class Error[T](error: Throwable) extends GeneratorEvent[T]
  case object Atom extends GeneratorEvent[Nothing]
  case object Seal extends GeneratorEvent[Nothing]

/** Generator. */
trait Generator[T]:
  import Generator.*

  /** Generates events for the atomic stream. */
  def generate(): GeneratorEvent[T]

  /** If the generator can produce more events. */
  // TODO: is this consistent with generators? consider removing.
  def hasNext(): Boolean
end Generator

/** Generator implementations */
private[portals] object GeneratorImpls:
  import Generator.*
  case class FromIterator[T](iterator: Iterator[T], keyExtractor: T => Key[Int] = ((x: T) => Key(x.hashCode())))
      extends Generator[T]:
    private val _iter = iterator.map(x => Event[T](keyExtractor(x), x)).concat(Iterator(Atom, Seal))
    def generate(): GeneratorEvent[T] =
      _iter.next()
    def hasNext(): Boolean =
      _iter.hasNext

  case class FromIteratorOfIterators[T](
      itit: Iterator[Iterator[T]],
      keyExtractor: T => Key[Int] = ((x: T) => Key(x.hashCode()))
  ) extends Generator[T]:
    private val _iter: Iterator[GeneratorEvent[T]] = itit
      .map { _.map { x => Event[T](keyExtractor(x), x) }.concat(Iterator.single(Atom)) }
      .concat(Iterator.single(Iterator.single(Seal)))
      .flatten

    def generate(): GeneratorEvent[T] = _iter.next()

    def hasNext(): Boolean = _iter.hasNext

/** Generator factories. */
object Generators:
  import Generator.*
  import GeneratorImpls.*

  def fromIterator[T](it: Iterator[T]): Generator[T] = FromIterator(it)

  def fromIteratorOfIterators[T](itit: Iterator[Iterator[T]]): Generator[T] = FromIteratorOfIterators(itit)

  // TODO: this feels fragile, rework this
  def fromIterator[T](it: Iterator[T], keys: Iterator[Key[Int]]): Generator[T] =
    FromIterator(it, _ => keys.next())

  // TODO: this feels fragile, rework this
  def fromIteratorOfIterators[T](itit: Iterator[Iterator[T]], keys: Iterator[Iterator[Key[Int]]]): Generator[T] =
    FromIteratorOfIterators(itit, _ => keys.flatten.next())

  def fromRange(start: Int, end: Int, step: Int): Generator[Int] =
    fromIteratorOfIterators(Iterator.range(start, end).grouped(step).map(_.iterator): Iterator[Iterator[Int]])
