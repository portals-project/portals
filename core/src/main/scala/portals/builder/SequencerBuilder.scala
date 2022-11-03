package portals

trait SequencerBuilder:
  def random[T](): AtomicSequencerRef[T]
end SequencerBuilder

object SequencerBuilder:
  def apply(name: String)(using bctx: ApplicationBuilderContext): SequencerBuilder =
    val _name = bctx.name_or_id(name)
    new SequencerBuilderImpl(_name)
end SequencerBuilder

class SequencerBuilderImpl(name: String)(using bctx: ApplicationBuilderContext) extends SequencerBuilder:
  private def build[T](_sequencer: Sequencer[T]): AtomicSequencerRef[T] =
    val _path = bctx.app.path + "/sequencers/" + name
    val _name = name
    val _ins = List.empty
    val aStream = AtomicStream[T](path = _path + "/stream")
    val _stream = AtomicStreamRef(aStream)
    val aSequencer = AtomicSequencer[T](
      path = _path,
      // ins = _ins,
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