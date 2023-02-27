package portals

import Common.Types.*

/** Splitter, can split atoms with filters to the corresponding paths. */
trait Splitter[T]:
  /** Split to a new output.
    *
    * @param path
    *   output path to be split to
    * @param filter
    *   filter function to be applied to filter out events for the output
    */
  def addOutput(path: Path, filter: Filter[T]): Unit

  /** Remove an output split.
    *
    * @param path
    *   output path to be removed
    */
  def removeOutput(path: Path): Unit

  /** Split an atom.
    *
    * @param atom
    *   atom to be split
    * @return
    *   list of (path, atom) pairs of the split atom
    */
  def split(atom: List[WrappedEvent[T]]): List[(Path, List[WrappedEvent[T]])]

object Splitters:
  /** Creates an empty splitter, one that does not have any splits yet, to be
    * used for adding new output splits.
    *
    * @tparam T
    *   event type of the splitter
    * @return
    *   empty splitter, with no current splits/filters
    */
  def empty[T](): Splitter[T] = new Splitter[T] {
    private var outputs: Map[Path, Filter[T]] = Map.empty

    override def addOutput(path: Path, filter: Filter[T]): Unit =
      outputs = outputs + (path -> filter)

    override def removeOutput(path: Path): Unit =
      outputs = outputs - path

    /** The suffix common to all split atoms. */
    private def suffix(atom: List[WrappedEvent[T]]): List[WrappedEvent[T]] =
      if atom.exists { case Error(t) => true; case _ => false } then
        val error = atom.find { case Error(t) => true; case _ => false }
        List(error.get, Atom, Seal)
      else if atom.exists { case Seal => true; case _ => false } then List(Seal)
      else if atom.exists { case Atom => true; case _ => false } then List(Atom)
      else ??? // should give error

    override def split(atom: List[WrappedEvent[T]]): List[(Path, List[WrappedEvent[T]])] =
      // special case if it is an empty atom with a seal event, then output seal on all outputs
      if atom == List(Seal) then outputs.map { case (path, _) => path -> List(Seal) }.toList
      else
        // assign correct path according to splits; group by paths
        atom
          // filter and keep all events
          .filter { case Event(_, _) => true; case _ => false }
          // assign correct path for each event
          .flatMap { event =>
            event match
              case Event(key, t) =>
                outputs.find { case (_, filter) => filter(t) }.map { case (path, _) => path -> event }
              case _ => ??? // can't occur
          }
          // group events by path
          .groupBy { case (path, _) =>
            path
          }
          // extract the events from the (path, event) pairs
          .map { case (path, events) =>
            path
            ->
            // path maps to the events
            events.map { case (_, event) => event }
          }
          // append the suffix to each created list of events
          .map { case (path, events) =>
            path -> events.appendedAll(suffix(atom))
          }
          .toList
  }
