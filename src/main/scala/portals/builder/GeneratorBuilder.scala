package portals

import portals.GeneratorImpls.ExternalRef

trait GeneratorBuilder:
  def fromIterator[T](it: Iterator[T]): AtomicGeneratorRef[T]

  def fromIterator[T](it: Iterator[T], keys: Iterator[Key[Int]]): AtomicGeneratorRef[T]

  def fromIteratorOfIterators[T](itit: Iterator[Iterator[T]]): AtomicGeneratorRef[T]

  def fromIteratorOfIterators[T](itit: Iterator[Iterator[T]], keys: Iterator[Iterator[Key[Int]]]): AtomicGeneratorRef[T]

  def fromList[T](list: List[T]): AtomicGeneratorRef[T]

  def fromList[T](list: List[T], keys: List[Key[Int]]): AtomicGeneratorRef[T]

  def fromListOfLists[T](listlist: List[List[T]]): AtomicGeneratorRef[T]

  def fromListOfLists[T](listlist: List[List[T]], keys: List[List[Key[Int]]]): AtomicGeneratorRef[T]

  def fromRange(start: Int, end: Int, step: Int): AtomicGeneratorRef[Int]

  def external[T](): (ExternalRef[T], AtomicGeneratorRef[T])

  def generator[T](g: Generator[T]): AtomicGeneratorRef[T]
end GeneratorBuilder

object GeneratorBuilder:
  def apply(name: String)(using bctx: ApplicationBuilderContext): GeneratorBuilder =
    val _name = bctx.name_or_id(name)
    new GeneratorBuilderImpl(_name)
end GeneratorBuilder

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

  override def fromIterator[T](it: Iterator[T], keys: Iterator[Key[Int]]): AtomicGeneratorRef[T] =
    val _generator = Generators.fromIterator[T](it, keys)
    this.build(_generator)

  override def fromIteratorOfIterators[T](itit: Iterator[Iterator[T]]): AtomicGeneratorRef[T] =
    val _generator = Generators.fromIteratorOfIterators(itit)
    this.build(_generator)

  override def fromIteratorOfIterators[T](
      itit: Iterator[Iterator[T]],
      keys: Iterator[Iterator[Key[Int]]]
  ): AtomicGeneratorRef[T] =
    val _generator = Generators.fromIteratorOfIterators(itit, keys)
    this.build(_generator)

  override def fromList[T](list: List[T]): AtomicGeneratorRef[T] =
    this.fromIterator(list.iterator)

  override def fromListOfLists[T](listlist: List[List[T]]): AtomicGeneratorRef[T] =
    this.fromIteratorOfIterators(listlist.map { _.iterator }.iterator)

  override def fromList[T](list: List[T], keys: List[Key[Int]]): AtomicGeneratorRef[T] =
    this.fromIterator(list.iterator, keys.iterator)

  override def fromListOfLists[T](listlist: List[List[T]], keys: List[List[Key[Int]]]): AtomicGeneratorRef[T] =
    this.fromIteratorOfIterators(listlist.map { _.iterator }.iterator, keys.map { _.iterator }.iterator)

  override def fromRange(start: Int, end: Int, step: Int): AtomicGeneratorRef[Int] =
    val _generator = Generators.fromRange(start, end, step)
    build(_generator)

  override def external[T](): (ExternalRef[T], AtomicGeneratorRef[T]) =
    val (_extRef, _generator) = Generators.external[T]()
    (_extRef, build(_generator))

  override def generator[T](g: Generator[T]): AtomicGeneratorRef[T] =
    build(g)
end GeneratorBuilderImpl
