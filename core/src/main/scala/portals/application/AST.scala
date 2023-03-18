package portals.application

import scala.scalajs.js.annotation.JSExportAll

import portals.application.generator.Generator
import portals.application.sequencer.Sequencer
import portals.application.splitter.Splitter
import portals.application.task.GenericTask

////////////////////////////////////////////////////////////////////////////////
// AST
////////////////////////////////////////////////////////////////////////////////

sealed trait AST:
  /** path of access from registry, path = parentPath/name */
  val path: String

////////////////////////////////////////////////////////////////////////////////
// Application
////////////////////////////////////////////////////////////////////////////////

/** Application. */
@JSExportAll
case class Application(
    path: String, // path of access from registry. this is /name for the app
    private[portals] workflows: List[Workflow[_, _]] = List.empty,
    private[portals] generators: List[AtomicGenerator[_]] = List.empty,
    private[portals] streams: List[AtomicStream[_]] = List.empty,
    private[portals] sequencers: List[AtomicSequencer[_]] = List.empty,
    private[portals] splitters: List[AtomicSplitter[_]] = List.empty,
    private[portals] connections: List[AtomicConnection[_]] = List.empty,
    private[portals] splits: List[AtomicSplit[_]] = List.empty,
    private[portals] portals: List[AtomicPortal[_, _]] = List.empty,
    private[portals] externalStreams: List[ExtAtomicStreamRef[_]] = List.empty,
    private[portals] externalSequencers: List[ExtAtomicSequencerRef[_]] = List.empty,
    private[portals] externalSplitters: List[ExtAtomicSplitterRef[_]] = List.empty,
    private[portals] externalPortals: List[ExtAtomicPortalRef[_, _]] = List.empty,
) extends AST

////////////////////////////////////////////////////////////////////////////////
// Workflow
////////////////////////////////////////////////////////////////////////////////

/** Workflow. */
@JSExportAll
case class Workflow[T, U](
    path: String,
    private[portals] consumes: AtomicStreamRefKind[T],
    stream: AtomicStreamRef[U],
    private[portals] tasks: Map[String, GenericTask[_, _, _, _]],
    private[portals] source: String,
    private[portals] sink: String,
    private[portals] connections: List[(String, String)]
) extends AST

////////////////////////////////////////////////////////////////////////////////
// Atomic Stream
////////////////////////////////////////////////////////////////////////////////

/** Atomic Stream Ref Kind. */
@JSExportAll
sealed trait AtomicStreamRefKind[T] extends AST

/** Atomic Stream. */
@JSExportAll
case class AtomicStream[T](path: String) extends AST

/** Atomic Stream Ref. */
@JSExportAll
case class AtomicStreamRef[T](path: String) extends AtomicStreamRefKind[T]

/** External Atomic Stream Ref. */
@JSExportAll
case class ExtAtomicStreamRef[T](path: String) extends AtomicStreamRefKind[T]

////////////////////////////////////////////////////////////////////////////////
// Atomic Sequencer
////////////////////////////////////////////////////////////////////////////////

/** Atomic Sequencer Ref. */
sealed trait AtomicSequencerRefKind[T] extends AST:
  val stream: AtomicStreamRefKind[T]

/** Atomic Sequencer. */
@JSExportAll
case class AtomicSequencer[T](
    path: String,
    stream: AtomicStreamRef[T],
    private[portals] sequencer: Sequencer[T]
) extends AST

/** Atomic Sequencer Ref. */
@JSExportAll
case class AtomicSequencerRef[T](path: String, stream: AtomicStreamRef[T]) extends AtomicSequencerRefKind[T]

/** External Atomic Sequencer Ref. */
@JSExportAll
case class ExtAtomicSequencerRef[T](path: String, stream: ExtAtomicStreamRef[T]) extends AtomicSequencerRefKind[T]

////////////////////////////////////////////////////////////////////////////////
// Atomic Splitter
////////////////////////////////////////////////////////////////////////////////

/** Atomic Splitter Ref. */
@JSExportAll
sealed trait AtomicSplitterRefKind[T] extends AST

/** Atomic Splitter. */
@JSExportAll
case class AtomicSplitter[T](
    path: String,
    private[portals] in: AtomicStreamRefKind[T],
    streams: List[AtomicStreamRefKind[T]],
    splitter: Splitter[T],
) extends AST

/** Atomic Splitter Ref. */
@JSExportAll
case class AtomicSplitterRef[T](path: String) extends AtomicSplitterRefKind[T]

/** External Atomic Splitter Ref. */
@JSExportAll
case class ExtAtomicSplitterRef[T](path: String) extends AtomicSplitterRefKind[T]

////////////////////////////////////////////////////////////////////////////////
// Atomic Split
////////////////////////////////////////////////////////////////////////////////

/** Atomic Split. */
@JSExportAll
case class AtomicSplit[T](
    path: String,
    private[portals] from: AtomicSplitterRefKind[T],
    private[portals] to: AtomicStreamRefKind[T],
    private[portals] filter: T => Boolean,
) extends AST

////////////////////////////////////////////////////////////////////////////////
// Atomic Generator
////////////////////////////////////////////////////////////////////////////////

/** Atomic Generator. */
@JSExportAll
case class AtomicGenerator[T](
    path: String,
    stream: AtomicStreamRef[T],
    private[portals] generator: Generator[T],
) extends AST

/** Atomic Generator Ref. */
@JSExportAll
case class AtomicGeneratorRef[T](path: String, stream: AtomicStreamRef[T]) extends AST

////////////////////////////////////////////////////////////////////////////////
// Atomic Connection
////////////////////////////////////////////////////////////////////////////////

/** Atomic Connection. */
@JSExportAll
case class AtomicConnection[T](
    path: String,
    private[portals] from: AtomicStreamRefKind[T],
    private[portals] to: AtomicSequencerRefKind[T],
) extends AST

////////////////////////////////////////////////////////////////////////////////
// Atomic Portal
////////////////////////////////////////////////////////////////////////////////

/** Atomic Portal Reference. */
@JSExportAll
case class AtomicPortalRef[T, R](
    path: String,
) extends AtomicPortalRefKind[T, R]

/** Atomic Portal. */
@JSExportAll
case class AtomicPortal[T, R](
    path: String,
    key: Option[T => Long] = None,
) extends AST

/** Atomic Portal Reference. */
@JSExportAll
sealed trait AtomicPortalRefKind[T, R] extends AST

/** External Atomic Portal Reference. */
@JSExportAll
case class ExtAtomicPortalRef[T, R](
    path: String,
) extends AtomicPortalRefKind[T, R]

////////////////////////////////////////////////////////////////////////////////
// Factories
////////////////////////////////////////////////////////////////////////////////

object AtomicStreamRef:
  def apply[T](astream: AtomicStream[T]): AtomicStreamRef[T] =
    AtomicStreamRef(astream.path)

object AtomicSequencerRef:
  def apply[T](asequencer: AtomicSequencer[T]): AtomicSequencerRef[T] =
    AtomicSequencerRef(asequencer.path, asequencer.stream)

object ExtAtomicSequencerRef:
  def apply[T](path: String): ExtAtomicSequencerRef[T] =
    val _path = path + "/" + "stream"
    val stream = ExtAtomicStreamRef[T](_path)
    ExtAtomicSequencerRef(path, stream)

object AtomicSplitterRef:
  def apply[T](asplitter: AtomicSplitter[T]): AtomicSplitterRef[T] =
    AtomicSplitterRef(asplitter.path)

object AtomicGeneratorRef:
  def apply[T](agen: AtomicGenerator[T]): AtomicGeneratorRef[T] =
    AtomicGeneratorRef(agen.path, agen.stream)

object AtomicPortalRef:
  def apply[T, R](aportal: AtomicPortal[T, R]): AtomicPortalRef[T, R] =
    AtomicPortalRef(aportal.path)
