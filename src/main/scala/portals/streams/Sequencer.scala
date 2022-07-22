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
  def sequence(streams: AtomicStreamRef[T]*): Option[AtomicStreamRef[T]]
end Sequencer

private[portals] object SequencerImpls:
  case class RandomSequencer[T]() extends Sequencer[T]:
    def sequence(streams: AtomicStreamRef[T]*): Option[AtomicStreamRef[T]] =
      if streams.isEmpty then None
      else Some(streams(Random.nextInt(streams.size)))
  end RandomSequencer

  case class RoundRobinSequencer[T]() extends Sequencer[T]:
    private var index = 0
    def sequence(streams: AtomicStreamRef[T]*): Option[AtomicStreamRef[T]] =
      if streams.isEmpty then None
      else Some(streams((index + 1) % streams.size))
  end RoundRobinSequencer
end SequencerImpls

object Sequencers:
  import SequencerImpls.*

  def random[T](): Sequencer[T] = RandomSequencer[T]()

  def roundRobin[T](): Sequencer[T] = RoundRobinSequencer[T]()
