package portals.compiler.phases.portal

import scala.util.Try

import portals.api.builder.TaskBuilder
import portals.application.*
import portals.application.sequencer.*
import portals.application.splitter.*
import portals.compiler.*
import portals.compiler.phases.portal.RewritePortalEvents.RewriteEvent
import portals.compiler.phases.portal.RewritePortalHelpers.*
import portals.runtime.WrappedEvents

/** Rewrite the portal definitions in the application. */
object RewritePortalsSubPhase extends CompilerSubPhase[Application, Application]:
  override def run(application: Application)(using ctx: CompilerContext): Application =
    // Note:
    // Rewrites Portals into Sequencers and Splitters; rewrites workflow sources
    // and sinks into sequencers respectively splitters.
    Try(application)
      // 1) Rewrite Portal definitions into a sequencer -> workflow -> splitter.
      .map(step1)
      // 2) For all workflows that are connected to portals, add a sequencer as
      // a new input to the workflow. Connect the sequencer to the original
      // input to the workflow (connection). Additionally, connect the sequencer
      // the portal splitter, by creating a new split, and connecting the split
      // stream to the sequencer.
      .map(step2)
      // 3) For all workflows that are connected to portals, create a splitter
      //  for the output of the workflow. Add one split for the original output
      // sink of the workflow, with the same path as the original output stream.
      // Additionally, add one new split for each connected portal, and
      // connect the split stream to the respective portal sequencer.
      .map(step3)
      // 4) Remove the portal definitions, references, from the application.
      .map(step4)
      .get
  end run

  /** Rewrite portal def into a sequencer and splitter.
    *
    * For every portal, rewrite the protal definitions into a sequencer ->
    * workflow -> splitter, for routing portal requests to the correct
    * workflows.
    */
  private def step1(application: Application): Application =
    given mapp: MutApplication = MutApplication(application)

    val portals = mapp.app.portals

    for portal <- portals do
      // PATHS
      val seqPath = portal.path.sequencer
      val wofPath = portal.path.workflow
      val sptPath = portal.path.splitter

      // SEQUENCER
      val atomicSequencer = addSequencer(seqPath)

      // WORKFLOW
      val wf =
        if portal.key.isDefined then
          Some(
            addKeyByWorkflow(
              wofPath,
              atomicSequencer.stream,
              portal.key.get.asInstanceOf[Any => Long],
            )
          )
        else None

      // SPLITTER
      val consumes = wf.map(_.stream).getOrElse(atomicSequencer.stream)
      val atomicSplitter = addSplitter(sptPath, consumes)

    mapp.app
  end step1

  /** Add sequencers to workflows.
    *
    * For all workflows that are connected to portals, add a sequencer as a new
    * input to the workflow. Connect the sequencer to the original input to the
    * workflow (connection). Additionally, connect the sequencer the portal
    * splitter, by creating a new split, and connecting the split stream to the
    * sequencer.
    */
  private def step2(application: Application): Application =
    given mapp: MutApplication = MutApplication(application)

    val workflows = mapp.app.workflows

    for workflow <- workflows.filter(hasPortal) do
      // PATHS
      val seqPath = workflow.path.sequencer

      // SEQUENCER
      val atomicSequencer = addSequencer(seqPath)
      // connect the consumed stream to the sequencer
      connectStreamWithSeq(
        seqPath.connection,
        workflow.consumes.asInstanceOf[AtomicStreamRefKind[Any]],
        atomicSequencer.ref
      )
      // update the consumed stream to the sequencer output
      mapp.app = mapp.app.copy(
        workflows = replacePathWith(
          workflow.path,
          workflow.copy(consumes = atomicSequencer.stream),
          mapp.app.workflows
        )
      )

      // SPLITS
      // split each portal output to the sequencer
      for portalRef <- extractAskingPortalRefs(workflow) do
        val filter: Any => Boolean =
          case RewriteEvent(WrappedEvents.Reply(_, meta, _)) =>
            meta.portal == portalRef.path && meta.askingWF == workflow.path
          case _ =>
            false
        val atomicSplit = addSplitToPortalSplitter(portalRef.path, freshId, filter)
        connectStreamWithSeq(atomicSplit.path.connection, atomicSplit.to, atomicSequencer.ref)

      // split each portal output to the sequencer
      for portalRef <- extractReplyingPortalRefs(workflow) do
        val filter: Any => Boolean =
          case RewriteEvent(WrappedEvents.Ask(_, meta, _)) =>
            meta.portal == portalRef.path
          case _ => false
        val atomicSplit = addSplitToPortalSplitter(portalRef.path, freshId, filter)
        connectStreamWithSeq(atomicSplit.path.connection, atomicSplit.to, atomicSequencer.ref)

    mapp.app
  end step2

  /** Add splitters to workflows.
    *
    * For all workflows that are connected to portals, create a splitter for the
    * output of the workflow. Add one split for the original output sink of the
    * workflow, with the same path as the original output stream. Additionally,
    * add one new split for each connected portal, and connect the split stream
    * to the respective portal sequencer.
    */
  private def step3(application: Application): Application =
    given mapp: MutApplication = MutApplication(application)

    for workflow <- mapp.app.workflows.filter(hasPortal) do
      // PATHS

      // WORKFLOW
      // original output stream
      val originalWorkflowStream = workflow.stream.asInstanceOf[AtomicStreamRefKind[Any]]
      // new hidden workflow output stream
      val newWorkflowStream = AtomicStream[Any](originalWorkflowStream.path.hidden)
      // update workflow
      mapp.app = mapp.app.copy(
        workflows = replacePathWith(
          workflow.path,
          workflow.copy(stream = newWorkflowStream.ref),
          mapp.app.workflows
        )
      )
      // add stream to app
      mapp.app = mapp.app.copy(
        streams = mapp.app.streams :+ newWorkflowStream
      )

      // SPLITTER 1 (original events)
      val atomicSplitter = addSplitter(workflow.path.splitter, newWorkflowStream.ref)
      val filter: Any => Boolean =
        case RewritePortalEvents.RewriteEvent(WrappedEvents.Ask(_, _, _)) => false
        case RewritePortalEvents.RewriteEvent(WrappedEvents.Reply(_, _, _)) => false
        case _ => true
      val split = addSplit(
        atomicSplitter.path.splits,
        atomicSplitter.ref,
        originalWorkflowStream,
        filter
      )

      // SPLITTER 2 (ask / reply events)
      // add a split for each portal and connect it to the portal
      for portalRef <- extractPortalsRefs(workflow) do
        val filter: Any => Boolean =
          case RewritePortalEvents.RewriteEvent(WrappedEvents.Ask(_, meta, _)) => meta.portal == portalRef.path
          case RewritePortalEvents.RewriteEvent(WrappedEvents.Reply(_, meta, _)) => meta.portal == portalRef.path
          case _ => false
        val splitPath = atomicSplitter.path.splits.id(freshId)
        val splitStream = AtomicStream[Any](splitPath.stream)
        mapp.app = mapp.app.copy(
          streams = mapp.app.streams :+ splitStream
        )
        val split = addSplit(
          splitPath,
          atomicSplitter.ref,
          splitStream.ref,
          filter,
        )
        val conn = connectStreamWithSeq(
          splitStream.path.connection,
          splitStream.ref,
          AtomicSequencerRef[Any](portalRef.path.sequencer, AtomicStreamRef[Any](portalRef.path.sequencer.stream)),
        )

    mapp.app
  end step3

  /** Remove the portal definitions from the application. */
  private def step4(application: Application): Application =
    application.copy(portals = List.empty, externalPortals = List.empty)
  end step4
end RewritePortalsSubPhase
