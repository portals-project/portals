package portals.runtime

import portals.application.Application

trait PortalsRuntime:
  /** Launch an application. */
  def launch(application: Application): Unit

  /** Terminate the runtime. */
  def shutdown(): Unit

end PortalsRuntime
