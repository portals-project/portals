package portals

// TODO: consider merging both Callbacks into one.
trait TaskCallback[T, U, Req, Rep]:
  // Task
  def submit(event: WrappedEvent[U]): Unit

  // Asker Task
  def ask(portal: AtomicPortalRefKind[Req, Rep], req: Req, key: Key[Int], id: Int): Unit

  // Replier Task
  def reply(r: Rep, key: Key[Int], id: Int): Unit
end TaskCallback // trait
