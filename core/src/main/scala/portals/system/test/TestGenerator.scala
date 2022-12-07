package portals.system.test

import portals.*

/** Internal API. TestGenerator. */
private[portals] class TestGenerator(val generator: AtomicGenerator[_])(using rctx: TestRuntimeContext):
  def process(): List[TestAtom] =
    var atom = List.empty[WrappedEvent[_]]
    var stop = false
    while generator.generator.hasNext() && !stop do
      generator.generator.generate() match
        case event @ Generator.Event(key, t) =>
          atom = Event(key, t) :: atom
        case Generator.Atom =>
          atom = Atom :: atom
          stop = true
        case Generator.Seal =>
          atom = Seal :: atom
          stop = true
        case Generator.Error(t) =>
          atom = Error(t) :: atom
          stop = true
    List(TestAtomBatch(generator.stream.path, atom.reverse))
