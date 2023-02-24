package portals

object Splitter:
  sealed trait SplitterEvent[+T]
  case class Event[T](key: Key[Long], t: T) extends SplitterEvent[T]
  case class Error[T](t: Throwable) extends SplitterEvent[T]
  case object Atom extends SplitterEvent[Nothing]
  case object Seal extends SplitterEvent[Nothing]

trait Splitter[T]:
  import Splitter.*
  type Atom = List[SplitterEvent[T]]
  type Path = String
  type Filter = T => Boolean
  // private type NoFilter <: Filter // should match every event
  def addOutput(path: Path, filter: Filter): Unit
  def removeOutput(path: Path): Unit
  def split(atom: Atom): List[(Path, Atom)]

object Splitters:
  import Splitter.*
  def empty[T](): Splitter[T] = new Splitter[T] {
    private var outputs: Map[Path, Filter] = Map.empty
    override def addOutput(path: Path, filter: Filter): Unit =
      outputs = outputs + (path -> filter)
    override def removeOutput(path: Path): Unit =
      outputs = outputs - path
    // TODO: should append atom/seal/error to all outs.
    override def split(atom: Atom): List[(Path, Atom)] =
      val has_atom = atom.exists { case Atom => true; case _ => false }
      val has_seal = atom.exists { case Seal => true; case _ => false }
      val error = atom.find { case Error(t) => true; case _ => false }
      println(atom)
      val suffix =
        if error.isDefined & has_atom then List(error.get, Atom, Seal)
        else if has_seal & has_atom then List(Atom, Seal)
        else if has_atom then List(Atom)
        else if has_seal then List(Seal)
        else List.empty // should really error

      if atom == List(Seal) then outputs.map { case (path, _) => path -> List(Seal) }.toList
      else
        atom
          .filter { case Event(_, _) => true; case _ => false }
          .asInstanceOf[List[Splitter.Event[T]]]
          .flatMap { e =>
            outputs.find { case (_, filter) => filter(e.t) }.map { case (path, _) => path -> e }
          }
          .groupBy { case (path, _) =>
            path
          }
          .map { case (path, events) =>
            path -> events
              .map { case (_, event) => event }
              .appendedAll(suffix)
          }
          .toList
  }
