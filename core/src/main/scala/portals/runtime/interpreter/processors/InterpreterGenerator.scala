package portals.runtime.interpreter.processors

import portals.application.AtomicGenerator
import portals.runtime.BatchedEvents.*
import portals.runtime.WrappedEvents.*
// import portals.Key

/** Internal API. TestGenerator. */
private[portals] class InterpreterGenerator(val generator: AtomicGenerator[_]) extends GeneratingStepper:
  override def step(): List[EventBatch] =
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
    List(AtomBatch(generator.stream.path, atom.reverse))
