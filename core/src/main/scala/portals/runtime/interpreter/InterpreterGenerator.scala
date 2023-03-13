package portals.runtime.interpreter

import portals.application.AtomicGenerator
import portals.runtime.interpreter.InterpreterEvents.*
import portals.runtime.WrappedEvents.*
// import portals.Key

/** Internal API. TestGenerator. */
private[portals] class InterpreterGenerator(val generator: AtomicGenerator[_])(using rctx: InterpreterRuntimeContext):
  def process(): List[InterpreterAtom] =
    var atom = List.empty[WrappedEvent[_]]
    var stop = false
    while generator.generator.hasNext() && !stop do
      generator.generator.generate() match
        case event @ Event(key, t) =>
          atom = Event(key, t) :: atom
        case Atom =>
          atom = Atom :: atom
          stop = true
        case Seal =>
          atom = Seal :: atom
          stop = true
        case Error(t) =>
          atom = Error(t) :: atom
          stop = true
        case Ask(_, _, _) =>
          ???
        case Reply(_, _, _) =>
          ???
    List(InterpreterAtomBatch(generator.stream.path, atom.reverse))
