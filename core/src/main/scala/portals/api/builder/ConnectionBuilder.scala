package portals.api.builder

import portals.*
import portals.application.*

/** Builder for Connections.
  *
  * Connect a stream to a sequencer using the ConnectionBuilder. This is done by
  * calling the `connect` method.
  *
  * @example
  *   {{{builder.connections.connect(stream, sequencer)}}}
  */
trait ConnectionBuilder:
  /** Connect a stream to a sequencer.
    *
    * @example
    *   {{{builder.connections.connect(stream, sequencer)}}}
    *
    * @param aStream
    *   the stream to connect
    * @param sequencer
    *   the sequencer to connect
    * @return
    *   the connection
    */
  def connect[T](aStream: AtomicStreamRefKind[T], sequencer: AtomicSequencerRefKind[T]): AtomicConnection[T]

private[portals] object ConnectionBuilder:
  /** Internal API. Create a ConnectionBuilder using the builder context. */
  def apply(name: String)(using bctx: ApplicationBuilderContext): ConnectionBuilder =
    val _name = bctx.name_or_id(name)
    new ConnectionBuilderImpl(_name)

/** Internal API. Implementation of the ConnectionBuilder. */
class ConnectionBuilderImpl(name: String)(using bctx: ApplicationBuilderContext) extends ConnectionBuilder:
  def connect[T](aStream: AtomicStreamRefKind[T], sequencer: AtomicSequencerRefKind[T]): AtomicConnection[T] =
    val path = bctx.app.path + "/connections/" + name
    val connection = AtomicConnection[T](path, aStream, sequencer)
    bctx.addToContext(connection)
    connection
