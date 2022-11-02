package portals.system.test

import portals.*
import portals.Generator

case class TestGenerator()(generator: AtomicGenerator[_])(using rctx: TestRuntimeContext):
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
    List(TestAtomBatch(generator.stream.path, atom))

  def inputs: Map[String, Long] = Map.empty

  def hasInput(): Boolean = generator.generator.hasNext()

  def cleanup(): Unit = ???
