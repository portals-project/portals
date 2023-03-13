package portals.api.builder

import portals.*
import portals.application.*

/** Build the registry to fetch references from the registry.
  *
  * Accessed from the application builder via `builder.registry`.
  *
  * @example
  *   {{{builder.registry.streams.get[String]("path/to/stream")}}}
  */
trait RegistryBuilder:
  /** Get a sequencer reference from the registry.
    *
    * @example
    *   {{{builder.registry.sequencers.get[String]("path/to/sequencer")}}}
    *
    * @return
    *   the sequencers registry
    */
  def sequencers: Registry[ExtAtomicSequencerRef]

  /** Get a splitter reference from the registry.
    *
    * @example
    *   {{{builder.registry.splitters.get[String]("path/to/splitter")}}}
    *
    * @return
    *   the splitters registry
    */
  def splitters: Registry[ExtAtomicSplitterRef]

  /** Get a stream reference from the registry.
    *
    * @example
    *   {{{builder.registry.streams.get[String]("path/to/stream")}}}
    *
    * @return
    *   the streams registry
    */
  def streams: Registry[ExtAtomicStreamRef]

  /** Get a portal reference from the registry.
    *
    * @example
    *   {{{builder.registry.portals.get[String, String]("path/to/portal")}}}
    *
    * @return
    *   the portals registry
    */
  def portals: Registry2[ExtAtomicPortalRef]
end RegistryBuilder

/** Internal API. The registry builder. */
object RegistryBuilder:
  /** Internal API. Create a RegistryBuilder using the application context. */
  def apply()(using ApplicationBuilderContext): RegistryBuilder = new RegistryBuilderImpl()

/** Internal API. Implementation of the RegistryBuilder. */
class RegistryBuilderImpl(using bctx: ApplicationBuilderContext) extends RegistryBuilder:
  override def sequencers: Registry[ExtAtomicSequencerRef] = new SequencerRegistryImpl()
  override def splitters: Registry[ExtAtomicSplitterRef] = new SplitterRegistryImpl()
  override def streams: Registry[ExtAtomicStreamRef] = new StreamsRegistryImpl()
  override def portals: Registry2[ExtAtomicPortalRef] = new PortalsRegistryImpl()

/** Internal API. A registry which has a single generic type parameter. */
trait Registry[A[_]]:
  /** Get a reference of `path` from the registry. */
  def get[T](path: String): A[T]
end Registry

/** Internal API. A registry which has two generic type parameters. */
trait Registry2[A[_, _]]:
  /** Get a reference of `path` from the registry. */
  def get[T, U](path: String): A[T, U]
end Registry2

/** Sequencer registry, fetch references of sequencers from the registry. */
class SequencerRegistryImpl(using bctx: ApplicationBuilderContext) extends Registry[ExtAtomicSequencerRef]:
  /** Get a sequencer reference to `path` from the registry.
    *
    * @example
    *   {{{builder.registry.sequencers.get[String]("path/to/sequencer")}}}
    *
    * @tparam T
    *   the event type of the sequencer
    * @param path
    *   the path to the sequencer
    * @return
    *   the sequencer reference
    */
  override def get[T](path: String): ExtAtomicSequencerRef[T] =
    val ref = ExtAtomicSequencerRef[T](path)
    bctx.addToContext(ref)
    ref

/** Splitter registry, fetch references of splitters from the registry. */
class SplitterRegistryImpl(using bctx: ApplicationBuilderContext) extends Registry[ExtAtomicSplitterRef]:
  /** Get a splitter reference to `path` from the registry.
    *
    * @example
    *   {{{builder.registry.splitters.get[String]("path/to/splitter")}}}
    *
    * @tparam T
    *   the event type of the splitter
    * @param path
    *   the path to the splitter
    * @return
    *   the splitter reference
    */
  override def get[T](path: String): ExtAtomicSplitterRef[T] =
    val ref = ExtAtomicSplitterRef[T](path)
    bctx.addToContext(ref)
    ref

/** Stream registry, fetch references of streams from the registry. */
class StreamsRegistryImpl(using bctx: ApplicationBuilderContext) extends Registry[ExtAtomicStreamRef]:
  /** Get a stream reference to `path` from the registry.
    *
    * @example
    *   {{{builder.registry.streams.get[String]("path/to/stream")}}}
    *
    * @tparam T
    *   the event type of the stream
    * @param path
    *   the path to the stream
    * @return
    *   the stream reference
    */
  override def get[T](path: String): ExtAtomicStreamRef[T] =
    val ref = ExtAtomicStreamRef[T](path)
    bctx.addToContext(ref)
    ref

/** Portal registry, fetch references of portals from the registry. */
class PortalsRegistryImpl(using bctx: ApplicationBuilderContext) extends Registry2[ExtAtomicPortalRef]:
  /** Get a portal reference to `path` from the registry.
    *
    * @example
    *   {{{builder.registry.portals.get[String, String]("path/to/portal")}}}
    *
    * @tparam T
    *   the request type of the portal
    * @tparam R
    *   the result type of the portal
    * @param path
    *   the path to the portal
    * @return
    *   the portal reference
    */
  override def get[T, R](path: String): ExtAtomicPortalRef[T, R] =
    val ref = ExtAtomicPortalRef[T, R](path)
    bctx.addToContext(ref)
    ref
