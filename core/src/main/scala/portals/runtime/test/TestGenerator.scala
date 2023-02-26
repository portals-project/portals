package portals

import portals.*

/** Internal API. TestGenerator. */
private[portals] class TestGenerator(val generator: AtomicGenerator[_])(using rctx: TestRuntimeContext):
  def process(): List[TestAtom] =
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
    List(TestAtomBatch(generator.stream.path, atom.reverse))
