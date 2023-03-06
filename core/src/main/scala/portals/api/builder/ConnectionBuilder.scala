package portals.api.builder

import portals.*
import portals.application.*

trait ConnectionBuilder:
  def connect[T](aStream: AtomicStreamRefKind[T], sequencer: AtomicSequencerRefKind[T]): AtomicConnection[T]

object ConnectionBuilder:
  def apply(name: String)(using bctx: ApplicationBuilderContext): ConnectionBuilder =
    val _name = bctx.name_or_id(name)
    new ConnectionBuilderImpl(_name)

class ConnectionBuilderImpl(name: String)(using bctx: ApplicationBuilderContext) extends ConnectionBuilder:
  def connect[T](aStream: AtomicStreamRefKind[T], sequencer: AtomicSequencerRefKind[T]): AtomicConnection[T] =
    val path = bctx.app.path + "/connections/" + name
    val connection = AtomicConnection[T](path, aStream, sequencer)
    bctx.addToContext(connection)
    connection
