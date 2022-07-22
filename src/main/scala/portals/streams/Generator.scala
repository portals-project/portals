package portals

/** Events to be produced by the generator. */
object Generator:
  sealed trait GeneratorEvent[+T]
  case class Event[T](value: T) extends GeneratorEvent[T]
  case object Atom extends GeneratorEvent[Nothing]
  case object Seal extends GeneratorEvent[Nothing]
  case class Error[T](error: Throwable) extends GeneratorEvent[T]

/** Generator. */
trait Generator[T]:
  import Generator.*

  /** Generates events for the atomic stream. */
  def generate(): GeneratorEvent[T]
  def hasNext(): Boolean
end Generator

/** Generator implementations */
private[portals] object GeneratorImpls:
  import Generator.*
  case class FromIterator[T](iterator: Iterator[T]) extends Generator[T]:
    val _iter = iterator.map(Event[T](_)).concat(Iterator(Atom, Seal))
    def generate(): GeneratorEvent[T] =
      _iter.next()
    def hasNext(): Boolean =
      _iter.hasNext

  case class FromFunction[T](f: () => GeneratorEvent[T], hasMore: () => Boolean) extends Generator[T]:
    def generate(): GeneratorEvent[T] = f()
    def hasNext(): Boolean = hasMore()

/** Generator factories. */
object Generators:
  import Generator.*
  import GeneratorImpls.*

  def fromIterator[T](it: Iterator[T]): Generator[T] = FromIterator(it)
  // def fromFunction[T](f: () => GeneratorEvent[T]): Generator[T] = FromFunction(f)
  def fromFunction[T](f: () => GeneratorEvent[T], hasNext: () => Boolean): Generator[T] = FromFunction(f, hasNext)
