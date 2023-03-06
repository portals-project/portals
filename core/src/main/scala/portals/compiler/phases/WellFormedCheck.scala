package portals.compiler.phases

import portals.*
import portals.application.task.ReplierTask
import portals.application.Application
import portals.compiler.*

/** Check if the application is well-formed. Throws exception. */
private[portals] object WellFormedCheck extends CompilerPhase[Application, Application]:
  override def run(application: Application)(using CompilerContext): Application =
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
