package portals

trait SplitBuilder:
  def split[T](splitter: AtomicSplitterRefKind[T], filter: T => Boolean): AtomicStreamRef[T]

object SplitBuilder:
  def apply(name: String)(using bctx: ApplicationBuilderContext): SplitBuilder =
    val _name = bctx.name_or_id(name)
    new SplitBuilderImpl(_name)
end SplitBuilder // object

class SplitBuilderImpl(name: String)(using bctx: ApplicationBuilderContext) extends SplitBuilder:
  override def split[T](splitter: AtomicSplitterRefKind[T], filter: T => Boolean): AtomicStreamRef[T] =
    val _name = bctx.name_or_id()
    val _path = splitter.path + "/" + _name
    val _split_path = splitter.path + "/" + "split" + "/" + bctx.name_or_id()
    val _newStream = AtomicStream[T](_path)
    val _newSplit = AtomicSplit[T](_split_path, AtomicSplitterRef(splitter.path), AtomicStreamRef(_newStream), filter)
    bctx.addToContext(_newStream)
    bctx.addToContext(_newSplit)
    AtomicStreamRef(_newStream)
end SplitBuilderImpl // class
