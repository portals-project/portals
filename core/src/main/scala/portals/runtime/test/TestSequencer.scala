package portals

import portals.*

/** Internal API. Test Runtime wrapper around the Sequencer. */
private[portals] class TestSequencer(sequencer: AtomicSequencer[_])(using rctx: TestRuntimeContext):
  // buffer for atoms, not used for random sequencer.
  private var streams: Map[String, List[List[WrappedEvent[_]]]] = Map.empty

  /** Optimization for the Random Sequencer, avoid allocating buffers. */
  private inline def process_random(atom: TestAtom): List[TestAtom] = atom match
    case TestAtomBatch(_, List(Atom)) => List.empty // consume empty atoms
    case TestAtomBatch(_, List(Seal)) => List.empty // consume seal
    // emit atoms without running the sequencer
    case TestAtomBatch(_, list) => List(TestAtomBatch(sequencer.stream.path, list))
    case _ => ??? // should not happen

  /** General method for processing atoms for the sequencer, might use buffers. */
  private inline def process_sequencer(atom: TestAtom): List[TestAtom] = atom match
    case TestAtomBatch(path, list) =>
      streams = streams.updated(path, streams.getOrElse(path, List.empty).appended(list))
      val choice = sequencer.sequencer.sequence(streams.keys.toList*)
      choice match
        case Some(p) =>
          // consume from the chosen stream
          val output = streams(p).head
          // update the streams buffers
          if streams(p).tail.isEmpty then streams = streams.removed(path)
          else streams = streams.updated(path, streams(p).tail)
          output match
            case List(Atom) => List.empty // consume empty atoms
            case List(Seal) => List.empty // consume seal
            // emit atoms without running the sequencer
            case list => List(TestAtomBatch(sequencer.stream.path, list))
        case None => ??? // might happen, but we will change things so this won't happen.
    case _ => ???

  /** Process an atom on the test sequencer. Will produce a List with a single atom, for now. */
  def process(atom: TestAtom): List[TestAtom] = sequencer.sequencer match
    case SequencerImpls.RandomSequencer() => process_sequencer(atom)
    case _ => process_sequencer(atom)
