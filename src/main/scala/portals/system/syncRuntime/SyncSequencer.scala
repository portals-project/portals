package portals

import java.util.LinkedList

class RuntimeSequencer[T](val staticSequencer: AtomicSequencer[T]) extends Executable, Recvable {

//   val upStreamBuffer = upStreams.map(s => s -> new LinkedList[AtomSeq]()).toMap
  var upStreamBuffer = Map[AtomicStreamRefKind[T], LinkedList[AtomSeq]]()
  var subscribers = Map[AtomicStreamRefKind[_], Recvable]() // note: seems always wf

  def subscribe(upStream: AtomicStreamRefKind[_]) = {
    upStreamBuffer = upStreamBuffer + (upStream.asInstanceOf[AtomicStreamRefKind[T]] -> new LinkedList[AtomSeq]())
  }

  override def recv(from: AtomicStreamRefKind[_], event: AtomSeq): Unit = {
    upStreamBuffer(from.asInstanceOf[AtomicStreamRefKind[T]]).add(event)
  }

  override def step(): Unit = {
    val nonEmptyUpstreamRefs = upStreamBuffer.filter(!_._2.isEmpty()).keySet.toList
    val selected = staticSequencer.sequencer.sequence(nonEmptyUpstreamRefs: _*).get
    subscribers.foreach(_._2.recv(staticSequencer.stream, upStreamBuffer(selected).poll()))
  }

  override def stepAll(): Unit = while (!isEmpty()) step()

  override def isEmpty(): Boolean = upStreamBuffer.forall(_._2.isEmpty)
}
