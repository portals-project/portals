package portals

trait SequencerBuilder:
  def random[T](name: String): AtomicSequencerRef[T]

  def roundRobin[T](name: String): AtomicSequencerRef[T]
end SequencerBuilder

object SequencerBuilder:
  def apply()(using ApplicationBuilderContext): SequencerBuilder =
    new SequencerBuilderImpl()
end SequencerBuilder

class SequencerBuilderImpl(using bctx: ApplicationBuilderContext) extends SequencerBuilder:
  override def random[T](name: String): AtomicSequencerRef[T] =
    val _path = bctx.app.path + "/" + name
    val _name = name
    val _ins = List.empty
    val aStream = AtomicStream[T](path = _path + "/stream", name = "stream")
    val _stream = AtomicStreamRef(aStream)
    val _sequencer = Sequencers.random[T]()
    val aSequencer = AtomicSequencer[T](
      path = _path,
      name = _name,
      ins = _ins,
      stream = _stream,
      sequencer = _sequencer,
    )
    bctx.addToContext(aSequencer)
    bctx.addToContext(aStream)
    AtomicSequencerRef(aSequencer)

  override def roundRobin[T](name: String): AtomicSequencerRef[T] =
    val _path = bctx.app.path + "/" + name
    val _name = name
    val _ins = List.empty
    val aStream = AtomicStream[T](path = _path + "/stream", name = "stream")
    val _stream = AtomicStreamRef(aStream)
    val _sequencer = Sequencers.roundRobin[T]()
    val aSequencer = AtomicSequencer[T](
      path = _path,
      name = _name,
      ins = _ins,
      stream = _stream,
      sequencer = _sequencer,
    )
    bctx.addToContext(aSequencer)
    bctx.addToContext(aStream)
    AtomicSequencerRef(aSequencer)
end SequencerBuilderImpl
