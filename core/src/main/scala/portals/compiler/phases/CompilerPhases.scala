package portals

object CompilerPhases:
  /** Check if the application is well-formed. Throws exception. */
  object WellFormedCheck extends CompilerPhase[Application, Application]:
    def run(application: Application)(using CompilerContext): Application =
      // 1. check for naming collissions
      // X: no two distinct elements may share the same path
      val allNames = application.workflows.map(_.path)
        ++ application.generators.map(_.path)
        ++ application.streams.map(_.path)
        ++ application.sequencers.map(_.path)
        // ++ application.splitters.map(_.path)
        ++ application.connections.map(_.path)
        ++ application.portals.map(_.path)
        ++ application.externalStreams.map(_.path)
        ++ application.externalSequencers.map(_.path)
        ++ application.externalPortals.map(_.path)
      if allNames.length != allNames.toSet.toList.length then ???

      // 2. check that a portal has a single replier
      // X: If an application defines a portal, then it must also define exactly one portal handler, and vice versa.
      // format: off
      val portals = application.portals.map(_.path)
      val portalTasks = application.workflows.map(_.tasks).flatMap(_.values)
      val _portals = portalTasks.filter { case r @ ReplierTask(_, _) => true; case _ => false }.flatMap(_.asInstanceOf[ReplierTask[_, _, _, _]].portals).map(_.path)
      if !(portals.length == _portals.length && portals.forall { _portals.contains(_) } && _portals.forall {portals.contains(_)}) then ???
      // format: on

      // forward the app
      application

// phases to be executed at runtime
object RuntimeCompilerPhases:
  /** Check if the application is well-formed with respect to the dynamic runtime. Throws exception. */
  def wellFormedCheck(application: Application)(using rctx: TestRuntimeContext): Unit =
    // 1. Check naming collision with other applications
    if rctx.applications.contains(application.path) then ???
    // this test should suffice, as all other paths will be a subpath of the application

    // 2. Check that all external references exist
    if !application.externalPortals.forall(x => rctx.portals.contains(x.path)) then ???
    if !application.externalSequencers.forall(x => rctx.sequencers.contains(x.path)) then ???
    if !application.externalStreams.forall(x => rctx.streams.contains(x.path)) then ???
