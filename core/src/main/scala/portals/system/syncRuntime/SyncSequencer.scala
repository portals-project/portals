package portals

import java.util.LinkedList

trait SyncSequencer extends Executable, Recvable {
  val staticSequencer: AtomicSequencer[_]
  def subscribe(upStream: AtomicStreamRefKind[_]): Unit
}

class RuntimeSequencer[T](val staticSequencer: AtomicSequencer[T]) extends SyncSequencer {

//   val upStreamBuffer = upStreams.map(s => s -> new LinkedList[AtomSeq]()).toMap
  var upStreamBuffer = Map[String, LinkedList[EventBatch]]()
  var subscribers = Set[Recvable]() // note: seems always wf

  private def getPath(stream: AtomicStreamRefKind[_]): String =
    stream match
      case AtomicStreamRef(path) => path
      case ExtAtomicStreamRef(path) => path

  def subscribe(upStream: AtomicStreamRefKind[_]) = {
    val path = getPath(upStream)
    upStreamBuffer = upStreamBuffer + (path -> new LinkedList[EventBatch]())
  }

  def subscribedBy(recvable: Recvable) = {
    subscribers = subscribers + recvable
  }

  override def recv(from: AtomicStreamRefKind[_], event: EventBatch): Unit = {
    val path = getPath(from)
    upStreamBuffer(path).add(event)
  }

  override def step(): Unit = {
    val nonEmptyUpstreamRefs = upStreamBuffer.filter(!_._2.isEmpty()).keySet.toList
    val refs = nonEmptyUpstreamRefs.map(AtomicStreamRef[T](_)).toList
    val selected = staticSequencer.sequencer.sequence(refs: _*).get
    subscribers.foreach(_.recv(staticSequencer.stream, upStreamBuffer(selected.path).poll()))
  }

  override def stepAll(): Unit = while (!isEmpty()) step()

  override def isEmpty(): Boolean = upStreamBuffer.forall(_._2.isEmpty)
}
