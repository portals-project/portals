package portals

trait OutputCollector[T, U, Req, Rep]:
  // Task
  def submit(event: WrappedEvent[U]): Unit

  // Asker Task
  def ask(portal: String, asker: String, req: Req, key: Key[Int], id: Int): Unit

  // Replier Task
  def reply(r: Rep, portal: String, asker: String, key: Key[Int], id: Int): Unit
end OutputCollector // trait
