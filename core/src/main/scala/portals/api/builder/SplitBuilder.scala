package portals.api.builder

import portals.application.*

/** Builder for splitting an atomic stream into multiple atomic streams.
  *
  * Splits can be added to a splitter via the `builder.splits.split` method.
  * Accessed from the application builder via `builder.splits`.
  *
  * @example
  *   {{{builder.splits.split[String](splitter, filter)}}}
  */
trait SplitBuilder:
  /** Add a split to a splitter.
    *
    * @example
    *   {{{builder.splits.split[String](splitter, filter)}}}
    *
    * @param splitter
    *   the splitter to add the split to
    * @param filter
    *   the filter to determine which events are filtered for the split
    * @return
    *   the new split atomic stream
    */
  def split[T](splitter: AtomicSplitterRefKind[T], filter: T => Boolean): AtomicStreamRef[T]

/** Internal API. The split builder. */
object SplitBuilder:
  /** Internal API. Create a split builder using the application context. */
  def apply(name: String)(using bctx: ApplicationBuilderContext): SplitBuilder =
    val _name = bctx.name_or_id(name)
    new SplitBuilderImpl(_name)
end SplitBuilder // object

/** Internal API. Implementation of the split builder. */
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
