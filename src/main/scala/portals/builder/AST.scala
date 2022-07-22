package portals

import scala.annotation.targetName

/** Registrable, can be added to or queried from the registry. */
sealed trait Registrable:
  val path: String // path of access from registry, path = parentPath/name
  val name: String // name

////////////////////////////////////////////////////////////////////////////////
// AST
////////////////////////////////////////////////////////////////////////////////

sealed trait AST extends Registrable

// TODO: consider if it is necessary that AST nodes (application, etc.) have a path, or if it can be omitted, or if there may be
// factories that don't need the path.

/** Application. */
case class Application(
    path: String, // path of access from registry. this is /name for the app
    name: String, // application name
    private[portals] workflows: List[Workflow[_, _]] = List.empty,
    private[portals] generators: List[AtomicGenerator[_]] = List.empty,
    // TODO: consider removing streams here, as they are implicit from the production of the workflows, sequencers, etc.
    private[portals] streams: List[AtomicStream[_]] = List.empty,
    private[portals] sequencers: List[AtomicSequencer[_]] = List.empty,
    private[portals] splitters: List[AtomicSplitter[_]] = List.empty,
    private[portals] connections: List[AtomicConnection[_]] = List.empty,
    private[portals] portals: List[AtomicPortal[_, _]] = List.empty,
    private[portals] externalStreams: List[ExtAtomicStreamRef[_]] = List.empty,
    private[portals] externalSequencers: List[ExtAtomicSequencerRef[_]] = List.empty,
    private[portals] externalPortals: List[ExtAtomicPortalRef[_, _]] = List.empty,
) extends AST

/** Workflow. */
case class Workflow[T, U](
    path: String,
    name: String,
    private[portals] consumes: AtomicStreamRef[T],
    stream: AtomicStreamRef[U],
    sequencer: Option[AtomicSequencerRef[T]],
    private[portals] tasks: Map[String, Task[_, _]],
    private[portals] sources: Map[String, Task[T, _]],
    private[portals] sinks: Map[String, Task[_, U]],
    private[portals] connections: List[(String, String)]
) extends AST

/** Atomic Stream. */
case class AtomicStream[T](path: String, name: String) extends AST

/** Atomic Stream Ref. */
case class AtomicStreamRef[T](path: String, name: String) extends AST

/** External Atomic Stream Ref. */
case class ExtAtomicStreamRef[T](path: String, name: String) extends AST

/** Atomic Sequencer. */
case class AtomicSequencer[T](
    path: String,
    name: String,
    private[portals] ins: List[AtomicStreamRef[T]],
    stream: AtomicStreamRef[T],
    private[portals] sequencer: Sequencer[T]
) extends AST

/** Atomic Sequencer Ref. */
case class AtomicSequencerRef[T](path: String, name: String, stream: AtomicStreamRef[T]) extends AST

/** External Atomic Sequencer Ref. */
case class ExtAtomicSequencerRef[T](path: String, name: String, stream: ExtAtomicStreamRef[T]) extends AST

/** Atomic Splitter. */
case class AtomicSplitter[T](
    path: String,
    name: String,
    private[portals] in: AtomicStreamRef[T],
    streams: List[AtomicStreamRef[T]],
    // splitter: Splitter[T], // TODO: implement
) extends AST

/** Atomic Splitter Ref. */
case class AtomicSplitterRef[T](path: String, name: String, streams: List[AtomicStreamRef[T]]) extends AST

/** Atomic Generator. */
case class AtomicGenerator[T](
    path: String,
    name: String,
    stream: AtomicStreamRef[T],
    private[portals] generator: Generator[T],
) extends AST

/** Atomic Generator Ref. */
case class AtomicGeneratorRef[T](path: String, name: String, stream: AtomicStreamRef[T]) extends AST

/** Atomic Connection. */
case class AtomicConnection[T](
    path: String,
    name: String,
    private[portals] from: AtomicStreamRef[T],
    private[portals] ti: AtomicSequencerRef[T],
) extends AST

/** Atomic Portal. */
case class AtomicPortal[T, R](
    path: String,
    name: String,
) extends AST

/** Atomic Portal Reference. */
case class AtomicPortalRef[T, R](
    path: String,
    name: String,
) extends AST

/** External Atomic Portal Reference. */
case class ExtAtomicPortalRef[T, R](
    path: String,
    name: String,
) extends AST

// TODO: is this necessary?
private[portals] type AtomicPortalRefType[Req, Rep] = (AtomicPortalRef[Req, Rep] | ExtAtomicPortalRef[Req, Rep])

////////////////////////////////////////////////////////////////////////////////
// Factories
////////////////////////////////////////////////////////////////////////////////
object AtomicStreamRef:
  def apply[T](astream: AtomicStream[T]): AtomicStreamRef[T] =
    AtomicStreamRef(astream.path, astream.name)

  @targetName("fromPath")
  def apply[T](path: String): AtomicStreamRef[T] =
    val name = path.split("/").last
    AtomicStreamRef(path = path, name = name)

object ExtAtomicStreamRef:
  def apply[T](path: String): ExtAtomicStreamRef[T] =
    val name = path.split("/").last
    ExtAtomicStreamRef(path = path, name = name)

object AtomicSequencerRef:
  def apply[T](asequencer: AtomicSequencer[T]): AtomicSequencerRef[T] =
    AtomicSequencerRef(asequencer.path, asequencer.name, asequencer.stream)

object ExtAtomicSequencerRef:
  def apply[T](path: String): ExtAtomicSequencerRef[T] =
    val name = "stream"
    val _path = path + "/" + name
    val stream = ExtAtomicStreamRef[T](path = _path, name = name)
    ExtAtomicSequencerRef(path, name, stream)

object AtomicSplitterRef:
  def apply[T](asplitter: AtomicSplitter[T]): AtomicSplitterRef[T] =
    AtomicSplitterRef(asplitter.path, asplitter.name, asplitter.streams)

object AtomicGeneratorRef:
  def apply[T](agen: AtomicGenerator[T]): AtomicGeneratorRef[T] =
    AtomicGeneratorRef(agen.path, agen.name, agen.stream)

object AtomicPortalRef:
  def apply[T, R](aportal: AtomicPortal[T, R]): AtomicPortalRef[T, R] =
    AtomicPortalRef(aportal.path, aportal.name)

  @targetName("fromPath")
  def apply[T, R](path: String): AtomicPortalRef[T, R] =
    val name = path.split("/").last
    AtomicPortalRef(path = path, name = name)

object ExtAtomicPortalRef:
  def apply[T, R](path: String): ExtAtomicPortalRef[T, R] =
    val name = "stream"
    val _path = path + "/" + name
    ExtAtomicPortalRef(path, name)
