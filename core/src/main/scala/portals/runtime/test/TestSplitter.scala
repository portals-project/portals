package portals

import portals.*
import portals.Splitter.SplitterEvent

/** Internal API. Test Runtime wrapper around the Splitter. */
private[portals] class TestSplitter(splitter: AtomicSplitter[_])(using rctx: TestRuntimeContext):

  /** Add an output to the splitter, that filters out the events for the path.
    *
    * @tparam X
    *   the type of the events that will be filtered.
    * @param path
    *   the path to which the filtered events will be sent.
    * @param filter
    *   the filter function that will be applied to the events.
    */
  def addOutput[X](path: String, filter: X => Boolean): Unit =
    // TODO: shouldn't have a type param
    splitter.splitter.addOutput(path, filter.asInstanceOf[Any => Boolean])

  /** Remove an output from the splitter.
    *
    * @param path
    *   the path of the output to remove.
    */
  def removeOutput(path: String): Unit = splitter.splitter.removeOutput(path)

  /** Create a list representation using the splitter events. */
  def toSplitterAtom(atom: TestAtom): List[SplitterEvent[Any]] =
    atom match
      case TestAtomBatch(path, list) =>
        list.map {
          case Event(key, t) => Splitter.Event(key, t)
          case Error(t) => Splitter.Error(t)
          case Atom => Splitter.Atom
          case Seal => Splitter.Seal
          case _ => ??? // should not happen
        }
      case _ => ??? // should not happen

  /** Create an atom representation from the splitter representation. */
  def fromSplitterAtom(path: String, satom: List[SplitterEvent[Any]]): TestAtom =
    TestAtomBatch(
      path,
      satom.map {
        case Splitter.Event(key, t) => Event(key, t)
        case Splitter.Error(t) => Error(t)
        case Splitter.Atom => Atom
        case Splitter.Seal => Seal
      }.toList
    )

  /** Process an atom on the test splitter. This will produce a list of new atoms, one for each nonempty output.
    *
    * @param atom
    *   the atom to process.
    * @return
    *   a list of new atoms, one for each nonempty output.
    */
  def process(atom: TestAtom): List[TestAtom] =
    splitter
      .asInstanceOf[AtomicSplitter[Any]]
      .splitter
      .split(toSplitterAtom(atom))
      .map(x => fromSplitterAtom(x._1, x._2))
