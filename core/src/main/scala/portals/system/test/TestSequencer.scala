package portals.system.test

import portals.*

// TODO: the sequencer should not buffer events like this :(, but we need to change
// the sequencer first.
class TestSequencer(sequencer: AtomicSequencer[_])(using rctx: TestRuntimeContext):
  private var streams: Map[String, List[List[WrappedEvent[_]]]] = Map.empty

  def process(atom: TestAtom): List[TestAtom] = atom match
    case TestAtomBatch(path, list) =>
      streams = streams.updated(path, streams.getOrElse(path, List.empty).appended(list))
      val choice = sequencer.sequencer.sequence(streams.keys.toList*)
      choice match
        case Some(p) =>
          val output = streams(p).head
          if streams(p).tail.isEmpty then streams = streams.removed(path)
          else streams = streams.updated(path, streams(p).tail)
          // CONSUME EMPTY ATOMS!
          if output == List(Atom) then List.empty
          // CONSUME SEALS
          else if output == List(Seal) then List.empty
          else List(TestAtomBatch(sequencer.stream.path, output))
        case None => ??? // might happen, but we will change things so this won't happen.
    case _ => ???
