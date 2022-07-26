package portals

trait GeneratorBuilder:
  def fromIterator[T](name: String, it: Iterator[T]): AtomicGeneratorRef[T]

  def fromIterator[T](name: String, it: Iterator[T], keys: Iterator[Key[Int]]): AtomicGeneratorRef[T]

  def fromIteratorOfIterators[T](name: String, itit: Iterator[Iterator[T]]): AtomicGeneratorRef[T]

  def fromIteratorOfIterators[T](
      name: String,
      itit: Iterator[Iterator[T]],
      keys: Iterator[Iterator[Key[Int]]]
  ): AtomicGeneratorRef[T]

  def fromList[T](name: String, list: List[T]): AtomicGeneratorRef[T]

  def fromList[T](name: String, list: List[T], keys: List[Key[Int]]): AtomicGeneratorRef[T]

  def fromListOfLists[T](name: String, listlist: List[List[T]]): AtomicGeneratorRef[T]

  def fromListOfLists[T](name: String, listlist: List[List[T]], keys: List[List[Key[Int]]]): AtomicGeneratorRef[T]

  def fromRange(name: String, start: Int, end: Int, step: Int): AtomicGeneratorRef[Int]

  def generator[T](name: String, g: Generator[T]): AtomicGeneratorRef[T]
end GeneratorBuilder

object GeneratorBuilder:
  def apply()(using ApplicationBuilderContext): GeneratorBuilder =
    new GeneratorBuilderImpl()
end GeneratorBuilder

// TODO: we have lots of duplicate code here, should refactor it out, same goes for other builders.
class GeneratorBuilderImpl(using bctx: ApplicationBuilderContext) extends GeneratorBuilder:
  override def fromIterator[T](name: String, it: Iterator[T]): AtomicGeneratorRef[T] =
    val _path = bctx.app.path + "/" + name
    val _name = name
    val _stream = AtomicStream[T](path = _path + "/stream", name = "stream")
    val _streamRef = AtomicStreamRef(_stream)
    val _generator = Generators.fromIterator[T](it)
    val agenerator = AtomicGenerator[T](
      path = _path,
      name = _name,
      stream = _streamRef,
      generator = _generator,
    )
    bctx.addToContext(_stream)
    bctx.addToContext(agenerator)
    AtomicGeneratorRef(agenerator)

  override def fromIterator[T](name: String, it: Iterator[T], keys: Iterator[Key[Int]]): AtomicGeneratorRef[T] =
    val _path = bctx.app.path + "/" + name
    val _name = name
    val _stream = AtomicStream[T](path = _path + "/stream", name = "stream")
    val _streamRef = AtomicStreamRef(_stream)
    val _generator = Generators.fromIterator[T](it, keys)
    val agenerator = AtomicGenerator[T](
      path = _path,
      name = _name,
      stream = _streamRef,
      generator = _generator,
    )
    bctx.addToContext(_stream)
    bctx.addToContext(agenerator)
    AtomicGeneratorRef(agenerator)

  override def fromIteratorOfIterators[T](name: String, itit: Iterator[Iterator[T]]): AtomicGeneratorRef[T] =
    val _path = bctx.app.path + "/" + name
    val _name = name
    val _stream = AtomicStream[T](path = _path + "/stream", name = "stream")
    val _streamRef = AtomicStreamRef(_stream)
    val _generator = Generators.fromIteratorOfIterators(itit)
    val agenerator = AtomicGenerator[T](
      path = _path,
      name = _name,
      stream = _streamRef,
      generator = _generator,
    )
    bctx.addToContext(_stream)
    bctx.addToContext(agenerator)
    AtomicGeneratorRef(agenerator)

  override def fromIteratorOfIterators[T](
      name: String,
      itit: Iterator[Iterator[T]],
      keys: Iterator[Iterator[Key[Int]]]
  ): AtomicGeneratorRef[T] =
    val _path = bctx.app.path + "/" + name
    val _name = name
    val _stream = AtomicStream[T](path = _path + "/stream", name = "stream")
    val _streamRef = AtomicStreamRef(_stream)
    val _generator = Generators.fromIteratorOfIterators(itit, keys)
    val agenerator = AtomicGenerator[T](
      path = _path,
      name = _name,
      stream = _streamRef,
      generator = _generator,
    )
    bctx.addToContext(_stream)
    bctx.addToContext(agenerator)
    AtomicGeneratorRef(agenerator)

  override def fromList[T](name: String, list: List[T]): AtomicGeneratorRef[T] =
    this.fromIterator(name, list.iterator)

  override def fromListOfLists[T](name: String, listlist: List[List[T]]): AtomicGeneratorRef[T] =
    this.fromIteratorOfIterators(name, listlist.map { _.iterator }.iterator)

  def fromList[T](name: String, list: List[T], keys: List[Key[Int]]): AtomicGeneratorRef[T] =
    this.fromIterator(name, list.iterator, keys.iterator)

  def fromListOfLists[T](name: String, listlist: List[List[T]], keys: List[List[Key[Int]]]): AtomicGeneratorRef[T] =
    this.fromIteratorOfIterators(name, listlist.map { _.iterator }.iterator, keys.map { _.iterator }.iterator)

  override def fromRange(name: String, start: Int, end: Int, step: Int): AtomicGeneratorRef[Int] =
    val _path = bctx.app.path + "/" + name
    val _name = name
    val _stream = AtomicStream[Int](path = _path + "/stream", name = "stream")
    val _streamRef = AtomicStreamRef(_stream)
    val _generator = Generators.fromRange(start, end, step)
    val agenerator = AtomicGenerator[Int](
      path = _path,
      name = _name,
      stream = _streamRef,
      generator = _generator,
    )
    bctx.addToContext(_stream)
    bctx.addToContext(agenerator)
    AtomicGeneratorRef(agenerator)

  override def generator[T](name: String, g: Generator[T]): AtomicGeneratorRef[T] =
    val _path = bctx.app.path + "/" + name
    val _name = name
    val _stream = AtomicStream[T](path = _path + "/stream", name = "stream")
    val _streamRef = AtomicStreamRef(_stream)
    val _generator = g
    val agenerator = AtomicGenerator[T](
      path = _path,
      name = _name,
      stream = _streamRef,
      generator = _generator,
    )
    bctx.addToContext(_stream)
    bctx.addToContext(agenerator)
    AtomicGeneratorRef(agenerator)
end GeneratorBuilderImpl
