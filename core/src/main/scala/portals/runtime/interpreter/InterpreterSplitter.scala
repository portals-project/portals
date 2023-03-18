package portals.runtime.interpreter

import portals.application.AtomicSplitter
import portals.runtime.interpreter.InterpreterEvents.*
import portals.runtime.interpreter.InterpreterRuntimeContext
import portals.runtime.WrappedEvents.*

/** Internal API. Test Runtime wrapper around the Splitter. */
private[portals] class InterpreterSplitter(val splitter: AtomicSplitter[_])(using rctx: InterpreterRuntimeContext):

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
  def toSplitterAtom(atom: InterpreterAtom): List[WrappedEvent[Any]] =
    atom match
      case InterpreterAtomBatch(path, list) =>
        list
      case _ => ???

  /** Create an atom representation from the splitter representation. */
  def fromSplitterAtom(path: String, satom: List[WrappedEvent[Any]]): InterpreterAtom =
    InterpreterAtomBatch(path, satom)

  /** Process an atom on the test splitter. This will produce a list of new
    * atoms, one for each nonempty output.
    *
    * @param atom
    *   the atom to process.
    * @return
    *   a list of new atoms, one for each nonempty output.
    */
  def process(atom: InterpreterAtom): List[InterpreterAtom] =
    splitter
      .asInstanceOf[AtomicSplitter[Any]]
      .splitter
      .split(toSplitterAtom(atom))
      .map(x => fromSplitterAtom(x._1, x._2))
