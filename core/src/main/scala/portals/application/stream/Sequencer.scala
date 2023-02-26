package portals

import scala.util.Random

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
