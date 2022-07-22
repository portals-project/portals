package portals

trait GeneratorBuilder:
  def fromIterator[T](name: String, it: Iterator[T]): AtomicGeneratorRef[T]

  def fromFunction[T](name: String, f: () => Generator.GeneratorEvent[T], g: () => Boolean): AtomicGeneratorRef[T]

  def generator[T](name: String, g: Generator[T]): AtomicGeneratorRef[T]
end GeneratorBuilder

object GeneratorBuilder:
  def apply()(using ApplicationBuilderContext): GeneratorBuilder =
    new GeneratorBuilderImpl()
end GeneratorBuilder

class GeneratorBuilderImpl(using bctx: ApplicationBuilderContext) extends GeneratorBuilder:
  override def fromIterator[T](name: String, it: Iterator[T]): AtomicGeneratorRef[T] =
    val _path = bctx.app.name + "/" + name
    val _name = name
    val _stream = AtomicStream[T](path = _path + "/stream", name = "stream")
    val _streamRef = AtomicStreamRef(_stream)
    val _generator = Generators.fromIterator((it))
    val agenerator = AtomicGenerator[T](
      path = _path,
      name = _name,
      stream = _streamRef,
      generator = _generator,
    )
    bctx.addToContext(_stream)
    bctx.addToContext(agenerator)
    AtomicGeneratorRef(agenerator)

  override def fromFunction[T](
      name: String,
      f: () => Generator.GeneratorEvent[T],
      g: () => Boolean
  ): AtomicGeneratorRef[T] =
    val _path = bctx.app.name + "/" + name
    val _name = name
    val _stream = AtomicStream[T](path = _path + "/stream", name = "stream")
    val _streamRef = AtomicStreamRef(_stream)
    // TODO: fix
    val _generator = Generators.fromFunction(f, g)
    val agenerator = AtomicGenerator[T](
      path = _path,
      name = _name,
      stream = _streamRef,
      generator = _generator,
    )
    bctx.addToContext(_stream)
    bctx.addToContext(agenerator)
    AtomicGeneratorRef(agenerator)

  override def generator[T](name: String, g: Generator[T]): AtomicGeneratorRef[T] =
    val _path = bctx.app.name + "/" + name
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
