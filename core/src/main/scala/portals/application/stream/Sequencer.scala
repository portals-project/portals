package portals

import scala.util.Random

// TODO: There is something awkward about the sequencer implementation at the moment.
// The current way in which things are sequenced through the sequence method is awkward to use.
// We should redesign this, and see what is better for writing strategies.

/** Sequencer. */
trait Sequencer[T] extends Serializable:
  /** Sequencing strategy.
    *
    * @param paths
    *   paths (with ready atoms) to be sequenced
    * @return
    *   Next stream to be consumed in the produced sequence, can also be None
    */
  def sequence(paths: String*): Option[String]
end Sequencer

private[portals] object SequencerImpls:
  case class RandomSequencer[T]() extends Sequencer[T]:
    def sequence(paths: String*): Option[String] =
      if paths.isEmpty then None
      else Some(paths(Random.nextInt(paths.size)))
  end RandomSequencer

end SequencerImpls

object Sequencers:
  import SequencerImpls.*

  def random[T](): Sequencer[T] = RandomSequencer[T]()
