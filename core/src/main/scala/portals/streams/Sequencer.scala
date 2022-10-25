package portals

import scala.util.Random

/** Sequencer. */
trait Sequencer[T] extends Serializable:
  /** Sequencing strategy.
    *
    * @param streams
    *   streams (with ready atoms) to be sequenced
    * @return
    *   Next stream to be consumed in the produced sequence, can also be None
    */
  def sequence(streams: AtomicStreamRefKind[T]*): Option[AtomicStreamRefKind[T]]
end Sequencer

private[portals] object SequencerImpls:
  case class RandomSequencer[T]() extends Sequencer[T]:
    def sequence(streams: AtomicStreamRefKind[T]*): Option[AtomicStreamRefKind[T]] =
      if streams.isEmpty then None
      else Some(streams(Random.nextInt(streams.size)))
  end RandomSequencer

end SequencerImpls

object Sequencers:
  import SequencerImpls.*

  def random[T](): Sequencer[T] = RandomSequencer[T]()
