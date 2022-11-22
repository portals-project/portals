package portals

// TODO: consider merging both Callbacks into one.
trait TaskCallback[T, U, Req, Rep]:
  // Task
  def submit(event: WrappedEvent[U]): Unit

  // Asker Task
  def ask(portal: String, portalAsker: String, replier: String, asker: String, req: Req, key: Key[Int], id: Int): Unit

  // Replier Task
  def reply(r: Rep, portal: String, portalAsker: String, replier: String, asker: String, key: Key[Int], id: Int): Unit
end TaskCallback // trait
