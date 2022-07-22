package portals

trait ConnectionBuilder:
  def connect[T](name: String, aStream: AtomicStreamRef[T], sequencer: AtomicSequencerRef[T]): AtomicConnection[T]

object ConnectionBuilder:
  def apply()(using bctx: ApplicationBuilderContext): ConnectionBuilder = new ConnectionBuilderImpl()

class ConnectionBuilderImpl(using bctx: ApplicationBuilderContext) extends ConnectionBuilder:
  def connect[T](name: String, aStream: AtomicStreamRef[T], sequencer: AtomicSequencerRef[T]): AtomicConnection[T] =
    val path = bctx.app.path + "/connections/" + name
    val connection = AtomicConnection[T](path, name, aStream, sequencer)
    bctx.addToContext(connection)
    connection
