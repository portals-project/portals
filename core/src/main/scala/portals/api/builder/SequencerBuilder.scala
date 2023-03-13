package portals.api.builder

import portals.*
import portals.application.*
import portals.application.sequencer.Sequencer
import portals.application.sequencer.Sequencers

/** Build sequencers.
  *
  * Sequencers are used to sequence multiple atomic streams into a single atomic
  * stream. Atomic streams are connected to a sequencer via using the
  * [[ConnectionBuilder]] `builder.connections.connect`.
  *
  * Accessed from the application builder via `builder.sequencers`.
  *
  * @example
  *   {{{builder.sequencers.random[String]()}}}
  */
trait SequencerBuilder:
  /** Create a random sequencer.
    *
    * @example
    *   {{{builder.sequencers.random[String]()}}}
    *
    * @return
    *   the sequencer reference
    */
  def random[T](): AtomicSequencerRef[T]
end SequencerBuilder

/** Internal API. The sequencer builder. */
object SequencerBuilder:
  /** Internal API. Create a SequencerBuilder using the application context. */
  def apply(name: String)(using bctx: ApplicationBuilderContext): SequencerBuilder =
    val _name = bctx.name_or_id(name)
    new SequencerBuilderImpl(_name)
end SequencerBuilder

/** Internal API. Implementation of the SequencerBuilder. */
class SequencerBuilderImpl(name: String)(using bctx: ApplicationBuilderContext) extends SequencerBuilder:
  private def build[T](_sequencer: Sequencer[T]): AtomicSequencerRef[T] =
    val _path = bctx.app.path + "/sequencers/" + name
    val aStream = AtomicStream[T](path = _path + "/stream")
    val _stream = AtomicStreamRef(aStream)
    val aSequencer = AtomicSequencer[T](
      path = _path,
      stream = _stream,
      sequencer = _sequencer,
    )
    bctx.addToContext(aSequencer)
    bctx.addToContext(aStream)
    AtomicSequencerRef(aSequencer)

  override def random[T](): AtomicSequencerRef[T] =
    val _sequencer = Sequencers.random[T]()
    build(_sequencer)

end SequencerBuilderImpl
