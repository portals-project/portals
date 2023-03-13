package portals.api.builder

import portals.*
import portals.application.*
import portals.application.splitter.Splitter
import portals.application.splitter.Splitters

/** Build splitters to split an atomic stream into multiple atomic streams.
  *
  * Accessed from the application builder via `builder.splitters`.
  *
  * @example
  *   {{{builder.splitters.empty[String](stream)}}}
  */
trait SplitterBuilder:
  /** Create an empty splitter with no splits from a `stream`.
    *
    * @example
    *   {{{builder.splitters.empty[String](stream)}}}
    *
    * @param stream
    *   the stream to split
    * @return
    *   the splitter reference with no splits
    */
  def empty[T](stream: AtomicStreamRefKind[T]): AtomicSplitterRef[T]
end SplitterBuilder // trait

/** Internal API. The splitter builder. */
object SplitterBuilder:
  /** Internal API. Create a SplitterBuilder using the application context. */
  def apply(name: String)(using bctx: ApplicationBuilderContext): SplitterBuilder =
    val _name = bctx.name_or_id(name)
    new SplitterBuilderImpl(_name)
end SplitterBuilder // trait

/** Internal API. Implementation of the splitter builder. */
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
