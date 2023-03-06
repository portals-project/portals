package portals.api.builder

import portals.*

trait SplitterBuilder:
  def empty[T](stream: AtomicStreamRefKind[T]): AtomicSplitterRef[T]
end SplitterBuilder // trait

object SplitterBuilder:
  def apply(name: String)(using bctx: ApplicationBuilderContext): SplitterBuilder =
    val _name = bctx.name_or_id(name)
    new SplitterBuilderImpl(_name)
end SplitterBuilder // trait

class SplitterBuilderImpl(name: String)(using bctx: ApplicationBuilderContext) extends SplitterBuilder:
  private def build[T](_stream: AtomicStreamRefKind[T], _splitter: Splitter[T]): AtomicSplitterRef[T] =
    val _path = bctx.app.path + "/splitters/" + name
    val _in = _stream
    val _streams = List.empty
    val aSplitter = AtomicSplitter[T](
      path = _path,
      in = _in,
      streams = _streams,
      splitter = _splitter,
    )
    bctx.addToContext(aSplitter)
    AtomicSplitterRef(aSplitter)

  override def empty[T](stream: AtomicStreamRefKind[T]): AtomicSplitterRef[T] =
    val _splitter = Splitters.empty[T]()
    this.build(stream, _splitter)
