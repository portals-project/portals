package portals.compiler.phases.portal

import portals.runtime.WrappedEvents
import portals.util.Key

/** Events used for the portal rewrite. */
private[portals] object RewritePortalEvents:
  //////////////////////////////////////////////////////////////////////////////
  // Rewrite Portal Events
  //////////////////////////////////////////////////////////////////////////////

  /** Event type used to wrap system events, and used as a normal user event. */
  private[portals] case class RewriteEvent[T](we: WrappedEvents.WrappedEvent[T])

  //////////////////////////////////////////////////////////////////////////////
  // Mapping from WrappedEvents to RewritePortalEvents
  //////////////////////////////////////////////////////////////////////////////

  extension (we: WrappedEvents.WrappedEvent[_])
    /** Convert a wrapped event to a rewrite event. */
    def toRewrite: RewriteEvent[_] =
      RewriteEvent(we)

  //////////////////////////////////////////////////////////////////////////////
  // Mapping from RewritePortalEvents to WrappedEvents
  //////////////////////////////////////////////////////////////////////////////

  extension (re: RewriteEvent[_])
    /** Convert a rewrite event to a wrapped event. */
    def toWrapped: WrappedEvents.WrappedEvent[_] =
      re.we

end RewritePortalEvents // object
