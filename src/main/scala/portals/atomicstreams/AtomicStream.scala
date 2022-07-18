package portals

// // Instead of launching a workflow, we will launch an `application` which contains
// //
// trait Application:
//   val path: String
//   val name: String
//   val workflows: List[Workflow[_, _]]
//   val streams: List[AtomicStream[_]]
//   val sequencers: List[AtomicSequencer[_]]
//   val connections: List[AtomicConnection[_]]
//   // val splitters: List[AtomicSplitter[_]] // omitted for now
//   val generators: List[AtomicGenerator[_]]
//   val externalStreams: List[ExternalAtomicStream[_]]
//   val externalSequencers: List[ExternalAtomicSequencer[_]]

// An atomic stream
trait AtomicStream[T]:
  /** Path of access from registry. */
  val path: String // path = parentPath/name

  /** Name of the atomic stream. */
  val name: String

// A reference to an atomic stream
trait AtomicStreamRef[T]:
  val path: String
  val name: String

// external atomic stream (from some other application)
trait ExternalAtomicStream[T]:
  val path: String

// The Atomic Sequencer
trait AtomicSequencer[T]:
  val path: String
  val name: String

  /** Reference to the produced atomic stream. */
  val stream: AtomicStreamRef[T]

  // val behavior:

trait AtomicSequencerRef[T]:
  val path: String
  val name: String

  /** Reference to the produced atomic stream. */
  val stream: AtomicStreamRef[T]

// external atomic sequencer (from some other application)
trait ExternalAtomicSequencer[T]:
  val path: String

// Connection from an atomic stream to an atomic sequencer
trait AtomicConnection[T]:
  val path: String
  val name: String
  val fromStream: AtomicStreamRef[T]
  val toSequencer: AtomicSequencerRef[T]

// Currently not used, wait for ports and composite streams
trait AtomicSplitter[T]:
  val path: String
  val name: String
  val outStreams: List[AtomicStream[T]]
  val inStream: AtomicStream[T]

// Generator of atomic stream
trait AtomicGenerator[T]:
  val path: String
  val name: String
  // the generated atomic stream
  val stream: AtomicStreamRef[T]
  // the generator itself
  val generator: Generator[T]

object Generator:
  sealed trait GeneratorEvents[+T]
  case class Event[T](value: T) extends GeneratorEvents[T]
  case object Atom extends GeneratorEvents[Nothing]
  case object Seal extends GeneratorEvents[Nothing]
  case class Error[T](error: Throwable) extends GeneratorEvents[T]
import Generator.*

trait Generator[T]:
  def generate(): GeneratorEvents[T]

// ignore the following for now :)

// object Generators:
//   def iterator[T](it: Iterator[GeneratorEvents[T]]): Generator[T] = GeneratorImpls.IteratorGenerator(it)

// object GeneratorImpls:
//   case class IteratorGenerator[T](iterator: Iterator[GeneratorEvents[T]]) extends Generator[T]:
//     def generate(): GeneratorEvents[T] = if iterator.hasNext then iterator.next() else ???

// trait GeneratorsBuilder:
//   def iterator[T](name: String, it: Iterator[Generator.GeneratorEvents[T]]): AtomicGenerator[T]

// object GeneratorsBuilder extends GeneratorsBuilder:

//   def iterator[T](name: String, it: Iterator[GeneratorEvents[T]]) =
//     val _path = "parent/" + name
//     val _name = name
//     val _stream = new AtomicStream[T] {
//       val path = _path + "/stream"
//       val name = "/stream"
//     }
//     val _behavior = Generators.iterator((it))
//     new AtomicGenerator[T]:
//       val path = _path
//       val name = _name
//       val stream = _stream
//       val behavior = _behavior

// object AtomicStreamsBuilder:
//   def generators: GeneratorsBuilder = GeneratorsBuilder

// package portals

// class AtomicStream[T](
//     private[portals] val path: String,
//     private[portals] val name: String,
// ):
//   override def toString(): String = toString(0)

//   def toString(indent: Int): String =
//     def space(indent: Int) = " " * indent
//     val sb = new StringBuilder("")
//     // application
//     sb ++= space(indent) + s"Application: $name\n"
//     // workflows
//     sb ++= space(indent + 1) + s"workflows: \n"
//     sb ++= s"${workflows.map( _.toString(indent + 2) ).mkString("\n")}"
//     sb.toString()
// end AtomicStream // class
