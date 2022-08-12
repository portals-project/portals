package portals

import java.util.LinkedList

trait SyncSequencer extends Executable, Recvable {
  val staticSequencer: AtomicSequencer[_]
  def subscribe(upStream: AtomicStreamRefKind[_]): Unit
}

class RuntimeSequencer[T](val staticSequencer: AtomicSequencer[T]) extends SyncSequencer {

//   val upStreamBuffer = upStreams.map(s => s -> new LinkedList[AtomSeq]()).toMap
  var upStreamBuffer = Map[AtomicStreamRefKind[T], LinkedList[AtomSeq]]()
  var subscribers = Set[Recvable]() // note: seems always wf

  def subscribe(upStream: AtomicStreamRefKind[_]) = {
    upStreamBuffer = upStreamBuffer + (upStream.asInstanceOf[AtomicStreamRefKind[T]] -> new LinkedList[AtomSeq]())
  }

  def subscribedBy(recvable: Recvable) = {
    subscribers = subscribers + recvable
  }

  override def recv(from: AtomicStreamRefKind[_], event: AtomSeq): Unit = {
    upStreamBuffer(from.asInstanceOf[AtomicStreamRefKind[T]]).add(event)
  }

  override def step(): Unit = {
    val nonEmptyUpstreamRefs = upStreamBuffer.filter(!_._2.isEmpty()).keySet.toList
    val selected = staticSequencer.sequencer.sequence(nonEmptyUpstreamRefs: _*).get
    subscribers.foreach(_.recv(staticSequencer.stream, upStreamBuffer(selected).poll()))
  }

  override def stepAll(): Unit = while (!isEmpty()) step()

  override def isEmpty(): Boolean = upStreamBuffer.forall(_._2.isEmpty)
}
