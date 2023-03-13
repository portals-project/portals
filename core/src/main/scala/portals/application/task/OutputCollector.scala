package portals.application.task

import portals.runtime.WrappedEvents.*
import portals.util.Key

trait OutputCollector[T, U, Req, Rep]:
  //////////////////////////////////////////////////////////////////////////////
  // Task
  //////////////////////////////////////////////////////////////////////////////
  def submit(event: WrappedEvent[U]): Unit

  //////////////////////////////////////////////////////////////////////////////
  // Asker Task
  //////////////////////////////////////////////////////////////////////////////
  // TODO: the param names are confusing, make it clear what they are heres
  def ask(portal: String, asker: String, req: Req, key: Key[Long], id: Int): Unit

  //////////////////////////////////////////////////////////////////////////////
  // Replier Task
  //////////////////////////////////////////////////////////////////////////////
  // TODO: the param names are confusing, make it clear what they are heres
  def reply(r: Rep, portal: String, asker: String, key: Key[Long], id: Int): Unit
end OutputCollector // trait
