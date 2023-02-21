package portals

trait SplitterBuilder:
  def empty[T](stream: AtomicStreamRef[T]): AtomicSplitter[T]

  def split[T](splitter: AtomicSplitter[T], filter: T => Boolean): AtomicStreamRef[T]
end SplitterBuilder

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

  override def split[T](splitter: AtomicSplitter[T], filter: T => Boolean): AtomicStreamRef[T] =
    val _name = bctx.name_or_id()
    val _path = splitter.path + "/" + _name
    val _split_path = splitter.path + "/" + "split" + "/" + bctx.name_or_id()
    splitter.splitter.addOutput(_path, filter)
    val _newStream = AtomicStream[T](_path)
    val _newSplit = AtomicSplit[T](_split_path, AtomicSplitterRef(splitter.path), AtomicStreamRef(_newStream))
    bctx.addToContext(_newStream)
    bctx.addToContext(_newSplit)
    AtomicStreamRef(_newStream)
