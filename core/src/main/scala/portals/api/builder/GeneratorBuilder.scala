package portals.api.builder

import scala.annotation.experimental

import portals.application.*
import portals.application.generator.Generator
import portals.application.generator.GeneratorImpls
import portals.application.generator.GeneratorImpls.ExternalRef
import portals.application.generator.Generators
import portals.util.Key

/** Builder for Generators.
  *
  * Create a generator using the GeneratorBuilder. This is done by calling one
  * of the methods of the GeneratorBuilder, e.g. `fromIterator`. Calling one of
  * the methods will automatically add the generator to the application context
  * that is being built. The methods return a reference to the generator, this
  * reference can be used by other parts of the application, for example
  * accessing the output stream of the generator.
  *
  * The generators either create a single atom of events, or they may create
  * multiple atoms of events. The generators that create only a single atom are
  * the `fromIterator` and `fromList` generators. The `fromIteratorOfIterators`
  * on the other hand creates a new atom per inner iterator that is returned by
  * the outer iterator.
  *
  * @example
  *   {{{
  * val builder = ApplicationBuilder("MyApp")
  * val generator = builder.generators.fromIterator[Int](Iterator(1, 2, 3))
  * val stream = generator.stream
  * val worfklow = builder.workflows[Int, Int].source(stream).map(_ + 1).logger().sink().freeze()
  *   }}}
  */
trait GeneratorBuilder:
  /** Create a generator from an iterator of events.
    *
    * This creates a single atom from the events of the iterator with type `T`.
    *
    * @example
    *   {{{builder.generators.fromIterator(Iterator(1, 2, 3))}}}
    *
    * @tparam T
    *   The type of the generated events.
    * @param it
    *   The iterator of events.
    * @return
    *   A reference to the generator.
    */
  def fromIterator[T](it: Iterator[T]): AtomicGeneratorRef[T]

  /** Create a generator from an iterator of events with keys specified by the
    * `keys` iterator.
    *
    * This creates a single atom from the events of the iterator with type `T`.
    * The keys of the events are specified by the `keys` iterator. The iterators
    * should be of the same length.
    *
    * @example
    *   {{{builder.generators.fromIterator(Iterator(1, 2, 3), Iterator(4, 5, 6))}}}
    *
    * @tparam T
    *   The type of the generated events.
    * @param it
    *   The iterator of events.
    * @param keys
    *   The iterator of keys.
    * @return
    *   A reference to the generator.
    */
  def fromIterator[T](it: Iterator[T], keys: Iterator[Key]): AtomicGeneratorRef[T]

  /** Create a generator from an iterator of iterators of events.
    *
    * This creates a new atom for each inner iterator that is returned by the
    * outer iterator. The type of the events is `T`.
    *
    * @example
    *   {{{
    * // Creates two atoms: Atom(1, 2, 3) and Atom(4, 5, 6)
    * builder.generators.fromIteratorOfIterators(Iterator(Iterator(1, 2, 3), Iterator(4, 5, 6)))
    *   }}}
    *
    * @tparam T
    *   The type of the generated events.
    * @param itit
    *   The iterator of iterators of events.
    * @return
    *   A reference to the generator.
    */
  def fromIteratorOfIterators[T](itit: Iterator[Iterator[T]]): AtomicGeneratorRef[T]

  /** Create a generator from an iterator of iterators of events with keys
    * specified by the `keys` iterator.
    *
    * This creates a new atom for each inner iterator that is returned by the
    * outer iterator. The type of the events is `T`. The keys of the events are
    * specified by the `keys` iterator. The iterators should be of the same
    * length.
    *
    * @example
    *   {{{
    * // Creates two atoms: Atom(1, 2, 3) and Atom(4, 5, 6)
    * // with keys (7, 8, 9) and (10, 11, 12)
    * builder.generators.fromIteratorOfIterators(
    *   Iterator(Iterator(1, 2, 3), Iterator(4, 5, 6)),
    *   Iterator(Iterator(7, 8, 9), Iterator(10, 11, 12))
    * )
    *   }}}
    *
    * @tparam T
    *   The type of the generated events.
    * @param itit
    *   The iterator of iterators of events.
    * @param keys
    *   The iterator of iterators of keys.
    * @return
    *   A reference to the generator.
    */
  def fromIteratorOfIterators[T](
      itit: Iterator[Iterator[T]],
      keys: Iterator[Iterator[Key]]
  ): AtomicGeneratorRef[T]

  /** Create a generator from a list of events.
    *
    * This creates a single atom from the events of the list with type `T`.
    *
    * @example
    *   {{{builder.generators.fromList(List(1, 2, 3))}}}
    *
    * @tparam T
    *   The type of the generated events.
    * @param list
    *   The list of events.
    * @return
    *   A reference to the generator.
    */
  def fromList[T](list: List[T]): AtomicGeneratorRef[T]

  /** Create a generator from a list of events with keys specified by the keys
    * list.
    *
    * This creates a single atom from the events of the list with type `T`. The
    * keys of the events are specified by the `keys` list. The lists should be
    * of the same length.
    *
    * @example
    *   {{{
    * // Creates a single atom: Atom(1, 2, 3) with keys (4, 5, 6)
    * builder.generators.fromList(List(1, 2, 3), List(4, 5, 6))
    *   }}}
    *
    * @tparam T
    *   The type of the generated events.
    * @param list
    *   The list of events.
    * @param keys
    *   The list of keys.
    * @return
    *   A reference to the generator.
    */
  def fromList[T](list: List[T], keys: List[Key]): AtomicGeneratorRef[T]

  /** Create a generator from a list of lists of events.
    *
    * This creates a new atom for each inner list that is returned by the outer
    * list. The type of the events is `T`.
    *
    * @example
    *   {{{
    * // Creates two atoms: Atom(1, 2, 3) and Atom(4, 5, 6)
    * builder.generators.fromListOfLists(List(List(1, 2, 3), List(4, 5, 6)))
    *   }}}
    *
    * @tparam T
    *   The type of the generated events.
    * @param listlist
    *   The list of lists of events.
    * @return
    *   A reference to the created generator.
    */
  def fromListOfLists[T](listlist: List[List[T]]): AtomicGeneratorRef[T]

  /** Create a generator from a list of lists of events, together with a list of
    * lists of keys.
    *
    * This creates a new atom for each inner list that is returned by the outer
    * list. The type of the events is `T`. The keys of the events are specified
    * by the `keys` list. The lists should be of the same length.
    *
    * @example
    *   {{{
    * // Creates two atoms: Atom(1, 2, 3) and Atom(4, 5, 6)
    * // with keys (7, 8, 9) and (10, 11, 12)
    * builder.generators.fromListOfLists(
    *   List(List(1, 2, 3), List(4, 5, 6)),
    *   List(List(7, 8, 9), List(10, 11, 12))
    * )
    *   }}}
    */
  def fromListOfLists[T](listlist: List[List[T]], keys: List[List[Key]]): AtomicGeneratorRef[T]

  /** Create a generator from a range of integers which are grouped into `step`
    * sized atoms.
    *
    * This creates atoms of size `step` with sequentially increasing integers.
    * The sequence starts at `start` and ends at `end - 1`.
    *
    * @example
    *   {{{
    * // Creates the following atoms: Atom(1, 2), Atom(3, 4), Atom(5, 6), Seal
    * builder.generators.fromRange(1, 7, 2)
    *   }}}
    *
    * @param start
    *   The start of the range.
    * @param end
    *   The end of the range (not inclusive).
    * @return
    *   A reference to the generator.
    */
  def fromRange(start: Int, end: Int, step: Int): AtomicGeneratorRef[Int]

  // @experimental
  // @deprecated
  // /** Internal API. Create a generator for external systems to directly interact
  //   * with. Events can be submitted to the returned `ExternalRef`. The
  //   * `AtomicGeneratorRef` is a reference to this generator which can be used to
  //   * further build the application.
  //   *
  //   * @tparam T
  //   *   The type of the generated events.
  //   */
  // def external[T](): (ExternalRef[T], AtomicGeneratorRef[T])

  /** Internal API. Create a generator from a generator implementation. */
  private[portals] def generator[T](g: Generator[T]): AtomicGeneratorRef[T]
end GeneratorBuilder

/** Internal API. The generator builder. */
object GeneratorBuilder:
  /** Internal API. Create a GeneratorBuilder using the application context. */
  def apply(name: String)(using bctx: ApplicationBuilderContext): GeneratorBuilder =
    val _name = bctx.name_or_id(name)
    new GeneratorBuilderImpl(_name)
end GeneratorBuilder

/** Internal API. Implementation of the generator. */
class GeneratorBuilderImpl(name: String)(using bctx: ApplicationBuilderContext) extends GeneratorBuilder:
  // build the generator, add it to build context, return reference
  private def build[T](_generator: Generator[T]): AtomicGeneratorRef[T] =
    val _path = bctx.app.path + "/generators/" + name
    val _stream = AtomicStream[T](path = _path + "/stream")
    val _streamRef = AtomicStreamRef(_stream)
    val agenerator = AtomicGenerator[T](
      path = _path,
      stream = _streamRef,
      generator = _generator,
    )
    bctx.addToContext(_stream)
    bctx.addToContext(agenerator)
    AtomicGeneratorRef(agenerator)

  override def fromIterator[T](it: Iterator[T]): AtomicGeneratorRef[T] =
    val _generator = Generators.fromIterator[T](it)
    this.build(_generator)

  override def fromIterator[T](it: Iterator[T], keys: Iterator[Key]): AtomicGeneratorRef[T] =
    val _generator = Generators.fromIterator[T](it, keys)
    this.build(_generator)

  override def fromIteratorOfIterators[T](itit: Iterator[Iterator[T]]): AtomicGeneratorRef[T] =
    val _generator = Generators.fromIteratorOfIterators(itit)
    this.build(_generator)

  override def fromIteratorOfIterators[T](
      itit: Iterator[Iterator[T]],
      keys: Iterator[Iterator[Key]]
  ): AtomicGeneratorRef[T] =
    val _generator = Generators.fromIteratorOfIterators(itit, keys)
    this.build(_generator)

  override def fromList[T](list: List[T]): AtomicGeneratorRef[T] =
    this.fromIterator(list.iterator)

  override def fromListOfLists[T](listlist: List[List[T]]): AtomicGeneratorRef[T] =
    this.fromIteratorOfIterators(listlist.map { _.iterator }.iterator)

  override def fromList[T](list: List[T], keys: List[Key]): AtomicGeneratorRef[T] =
    this.fromIterator(list.iterator, keys.iterator)

  override def fromListOfLists[T](listlist: List[List[T]], keys: List[List[Key]]): AtomicGeneratorRef[T] =
    this.fromIteratorOfIterators(listlist.map { _.iterator }.iterator, keys.map { _.iterator }.iterator)

  override def fromRange(start: Int, end: Int, step: Int): AtomicGeneratorRef[Int] =
    val _generator = Generators.fromRange(start, end, step)
    build(_generator)

  // @experimental
  // @deprecated
  // override def external[T](): (ExternalRef[T], AtomicGeneratorRef[T]) =
  //   val (_extRef, _generator) = Generators.external[T]()
  //   (_extRef, build(_generator))

  override def generator[T](g: Generator[T]): AtomicGeneratorRef[T] =
    build(g)
end GeneratorBuilderImpl
