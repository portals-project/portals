package portals

trait TaskCallback[T, U, Req, Rep]:
  // Task
  def submit(event: WrappedEvent[U]): Unit

  // Asker Task
  def ask(portal: String, asker: String, req: Req, key: Key[Long], id: Int): Unit

  // Replier Task
  def reply(r: Rep, portal: String, asker: String, key: Key[Long], id: Int): Unit
end TaskCallback // trait
