package portals

private[portals] class RuntimePortal(portal: AtomicPortal[_, _]):
  var q = List.empty[WrappedEvent[_]]
  var subscribers = List.empty[Recvable]

  def enqueue(t: WrappedEvent[_]): Unit =
    q = t :: q

  def subscribe(recvable: Recvable): Unit =
    subscribers = recvable :: subscribers

  def distribute(): Unit =
    if !q.isEmpty then
      enqueue(Atom)
      val atom = AtomSeq(q)
      q = List.empty
      subscribers.foreach { sub =>
        sub.recv(AtomicStreamRef(portal.path), atom)
      }
