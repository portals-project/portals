package portals.system.test

import portals.*
import portals.Generator

case class TestSequencer(sequencer: AtomicSequencer[_])(using rctx: TestRuntimeContext):
  private var streams: Map[String, List[List[WrappedEvent[_]]]] = Map.empty
  var _inputs: Set[String] = Set.empty

  def process(atom: TestAtom): List[TestAtom] = atom match
    // we should consider changing the way the sequencer works for now, no strategy :).
    case TestAtomBatch(path, list) =>
      streams = streams.updated(path, streams.getOrElse(path, List.empty).appended(list))
      val choice = sequencer.sequencer.sequence(streams.keys.map(p => AtomicStreamRef(p)).toList*)
      choice match
        case Some(p) =>
          val output = streams(p.path).head
          streams = streams.updated(path, streams(p.path).tail)
          List(TestAtomBatch(sequencer.stream.path, output))
        case None => ??? // might happen, but we'll change things so it shouldn't happen soon

    // case TestPortalBatch(path, list) => ??? // shouldn't happen

  def inputs: Set[String] = _inputs

  // def hasInput(): Boolean =
  //   rctx.streams
  //     .filter((path, stream) => this.inputs.contains(path))
  //     .exists((path, stream) => inputs(path) < stream.getIdxRange()._2)

  def cleanup(): Unit = () // do nothing :)
