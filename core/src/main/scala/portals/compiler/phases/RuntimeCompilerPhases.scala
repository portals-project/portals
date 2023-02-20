package portals.compiler.phases

import portals.*

// phases to be executed at runtime
private[portals] object RuntimeCompilerPhases:
  /** Check if the application is well-formed with respect to the dynamic runtime. Throws exception. */
  def wellFormedCheck(application: Application)(using rctx: TestRuntimeContext): Unit =
    // 1. Check naming collision with other applications
    if rctx.applications.contains(application.path) then ???
    // this test should suffice, as all other paths will be a subpath of the application

    // 2. Check that all external references exist
    if !application.externalPortals.forall(x => rctx.portals.contains(x.path)) then ???
    if !application.externalSequencers.forall(x => rctx.sequencers.contains(x.path)) then ???
    if !application.externalStreams.forall(x => rctx.streams.contains(x.path)) then ???
