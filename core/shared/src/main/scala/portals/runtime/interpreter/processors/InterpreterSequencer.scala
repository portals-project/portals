package portals.runtime.interpreter.processors

import portals.application.sequencer.SequencerImpls
import portals.application.AtomicSequencer
import portals.runtime.BatchedEvents.*
import portals.runtime.WrappedEvents.*

/** Internal API. Test Runtime wrapper around the Sequencer. */
private[portals] class InterpreterSequencer(sequencer: AtomicSequencer[_]) extends ProcessingStepper:
  // buffer for atoms, not used for random sequencer.
  private var streams: Map[String, List[List[WrappedEvent[_]]]] = Map.empty

  /** Optimization for the Random Sequencer, avoid allocating buffers. */
  private inline def process_random(atom: EventBatch): List[EventBatch] = atom match
    case AtomBatch(_, List(Atom)) => List.empty // consume empty atoms
    case AtomBatch(_, List(Seal)) => List.empty // consume seal
    // emit atoms without running the sequencer
    case AtomBatch(_, list) => List(AtomBatch(sequencer.stream.path, list))
    case _ => ??? // should not happen

  /** General method for processing atoms for the sequencer, might use buffers.
    */
  private inline def process_sequencer(atom: EventBatch): List[EventBatch] = atom match
    case AtomBatch(path, list) =>
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
            case List(Atom) =>
              List.empty // consume empty atoms
            case List(Seal) => List.empty // consume seal
            // emit atoms without running the sequencer
            case list =>
              List(AtomBatch(sequencer.stream.path, list))
        case None => ??? // might happen, but we will change things so this won't happen.
    case _ => ???

  /** Process an atom on the test sequencer. Will produce a List with a single
    * atom, for now.
    */
  override def step(atom: EventBatch): List[EventBatch] = sequencer.sequencer match
    case SequencerImpls.RandomSequencer() => process_random(atom).filter(_.nonEmpty)
    case _ => process_sequencer(atom).filter(_.nonEmpty)
