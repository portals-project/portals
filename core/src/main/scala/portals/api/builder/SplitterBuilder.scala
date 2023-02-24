package portals

trait SplitterBuilder:
  def empty[T](stream: AtomicStreamRef[T]): AtomicSplitter[T]
end SplitterBuilder // trait

object SplitterBuilder:
  def apply(name: String)(using bctx: ApplicationBuilderContext): SplitterBuilder =
    val _name = bctx.name_or_id(name)
    new SplitterBuilderImpl(_name)
end SplitterBuilder // trait

class SplitterBuilderImpl(name: String)(using bctx: ApplicationBuilderContext) extends SplitterBuilder:
  private def build[T](_stream: AtomicStreamRef[T], _splitter: Splitter[T]): AtomicSplitter[T] =
    val _path = bctx.app.path + "/splitters/" + name
    val _name = name
    val _in = _stream
    val _streams = List.empty
    val aSplitter = AtomicSplitter[T](
      path = _path,
      in = _in,
      streams = _streams,
      splitter = _splitter,
    )
    bctx.addToContext(aSplitter)
    aSplitter

  override def empty[T](stream: AtomicStreamRef[T]): AtomicSplitter[T] =
    val _splitter = Splitters.empty[T]()
    this.build(stream, _splitter)
