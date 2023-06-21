package portals.runtime.interpreter

import scala.util.Random

import portals.application.*
import portals.application.task.*
import portals.compiler.phases.RuntimeCompilerPhases
import portals.runtime.*
import portals.runtime.interpreter.processors.*
import portals.runtime.interpreter.InterpreterContext
import portals.runtime.BatchedEvents.*
import portals.runtime.WrappedEvents.*
import portals.util.Common.Types.Path

private[portals] class Interpreter(ctx: InterpreterContext) extends SuspendingRuntime with GarbageCollectingRuntime:
  import SuspendingRuntime.*

  private def launchStreams(application: Application): Unit = {
    application.streams.foreach { stream =>
      ctx.rctx.addStream(stream)
      ctx.streamTracker.initStream(stream.path)
    }
  }

  private def launchPortals(application: Application): Unit = {
    application.portals.foreach { portal =>
      ctx.rctx.addPortal(portal)
    }
  }

  private def launchWorkflows(application: Application): Unit = {
    application.workflows.foreach { wf =>
      ctx.rctx.addWorkflow(wf)
      ctx.graphTracker.addEdge(wf.consumes.path, wf.path)
      ctx.graphTracker.addEdge(wf.path, wf.stream.path)
      ctx.progressTracker.initProgress(wf.path, wf.consumes.path)

      // add portal dependencies
      wf.tasks.foreach((name, task) =>
        task match
          case atask @ AskerTask(_) => List.empty
          case rtask @ ReplierTask(_, _) =>
            rtask.portals.foreach(p => ctx.rctx.portals(p.path).replier = wf.path)
            rtask.portals.foreach(p => ctx.rctx.portals(p.path).replierTask = name)
          case artask @ AskerReplierTask(_, _) =>
            artask.replyerportals.foreach(p => ctx.rctx.portals(p.path).replier = wf.path)
            artask.replyerportals.foreach(p => ctx.rctx.portals(p.path).replierTask = name)
          case _ => List.empty
      )
    }
  }

  private def launchSplitters(application: Application): Unit = {
    application.splitters.foreach { splitr =>
      ctx.rctx.addSplitter(splitr)
      ctx.graphTracker.addEdge(splitr.in.path, splitr.path)
      ctx.progressTracker.initProgress(splitr.path, splitr.in.path)
    }
  }

  private def launchSplits(application: Application): Unit = {
    application.splits.foreach { split =>
      ctx.rctx.splitters(split.from.path).addOutput(split.to.path, split.filter)
    }
  }

  private def launchSequencers(application: Application): Unit = {
    application.sequencers.foreach { seqr =>
      ctx.rctx.addSequencer(seqr)
      ctx.graphTracker.addEdge(seqr.path, seqr.stream.path)
    }
  }

  private def launchConnections(application: Application): Unit = {
    application.connections.foreach { conn =>
      ctx.rctx.addConnection(conn)
      ctx.graphTracker.addEdge(conn.from.path, conn.to.path)
      ctx.progressTracker.initProgress(conn.to.path, conn.from.path)
    }
  }

  private def launchGenerators(application: Application): Unit = {
    application.generators.foreach { genr =>
      ctx.rctx.addGenerator(genr)
      ctx.graphTracker.addEdge(genr.path, genr.stream.path)
    }
  }

  override def launch(application: Application): Unit =
    // check and add app if valid
    this.check(application)
    ctx.rctx.addApplication(application)

    // necessarily called in this sequence
    launchStreams(application)
    launchPortals(application)
    launchWorkflows(application)
    launchSplitters(application)
    launchSplits(application)
    launchSequencers(application)
    launchConnections(application)
    launchGenerators(application)

  override def shutdown(): Unit = ()

  override def feedAtoms(listOfAtoms: List[EventBatch]): Unit =
    listOfAtoms.foreach {
      case ta @ AtomBatch(path, list) =>
        ctx.rctx.streams(path).enqueue(ta)
        ctx.streamTracker.incrementProgress(path)
      case tpa @ AskBatch(meta, _) =>
        ctx.rctx.portals(meta.portal).enqueue(tpa)
      case tpr @ ReplyBatch(meta, _) =>
        ctx.rctx.portals(meta.portal).enqueue(tpr)
      case _ => ???
    }

  override def garbageCollect(): Unit =
    // Cleanup streams, compute min progress of stream dependents, and adjust accoringly
    ctx.rctx.streams.foreach { (streamName, stream) =>
      val streamProgress = ctx.streamTracker.getProgress(streamName).get
      val outputs = ctx.graphTracker.getOutputs(streamName).get
      val minprogress = outputs
        .map { outpt => ctx.progressTracker.getProgress(outpt, streamName).get }
        .minOption
        .getOrElse(streamProgress._2)
      if minprogress > streamProgress._1 then
        stream.prune(minprogress)
        ctx.streamTracker.setProgress(streamName, minprogress, streamProgress._2)
    }

  private def wrapEvents(path: Path, list: List[EventBatch]): StepResult =
    def p(x: EventBatch): Boolean = x match { case ShuffleBatch(_, _, _) => true; case _ => false }
    if list.exists(p) then
      Suspended(
        path = path,
        shuffle = list.filter(p).asInstanceOf[List[ShuffleBatch[_]]],
        intermediate = list.filterNot(p)
      )
    else Completed(path, list)

  override def resume(path: Path, shuffles: List[ShuffleBatch[_]]): StepResult =
    val outputs =
      shuffles.flatMap:
        case x @ ShuffleBatch(path, task, list) =>
          val wf = ctx.rctx.workflows(path)
          wf.step(x)
    wrapEvents(path, outputs) match
      case r @ Completed(_, _) =>
        ctx.suspendingTracker.remove(path)
        r
      case r @ Suspended(_, _, _) =>
        r

  override def step(): StepResult =
    choosePortal() match
      case Some(path, portal) => stepPortal(path, portal)
      case None =>
        chooseWorkflow() match
          case Some(path, wf) => stepStepper(path, wf)
          case None =>
            chooseSequencer() match
              case Some(path, seqr) => stepStepper(path, seqr)
              case None =>
                chooseSplitter() match
                  case Some(path, spltr) => stepStepper(path, spltr)
                  case None =>
                    chooseGenerator() match
                      case Some(path, genr) =>
                        stepStepper(path, genr)
                      case None => ???

  override def canStep(): Boolean =
    // use || so that we do not evaluate the other options unnecessarily
    choosePortal().isDefined
      || chooseWorkflow().isDefined
      || chooseSequencer().isDefined
      || chooseSplitter().isDefined
      || chooseGenerator().isDefined

  private def hasInput(path: String, dependency: String): Boolean =
    val progress = ctx.progressTracker.getProgress(path, dependency).get
    val streamProgress = ctx.streamTracker.getProgress(dependency).get._2
    progress < streamProgress

  private def hasInput(path: String): Boolean =
    val inputs = ctx.graphTracker.getInputs(path).get
    inputs.exists(inpt => hasInput(path, inpt))

  private inline def randomSelection[T](from: Map[String, T], predicate: (String, T) => Boolean): Option[(String, T)] =
    if from.size == 0 then None
    else
      val nxt = ctx.rnd.nextInt(from.size)
      from.drop(nxt).find((s, t) => predicate(s, t)) match
        case x @ Some(v) => x
        case None =>
          from.take(nxt).find((s, t) => predicate(s, t)) match
            case x @ Some(v) => x
            case None => None

  private def choosePortal(): Option[(String, InterpreterPortal)] =
    randomSelection(ctx.rctx.portals, (path, portal) => !portal.isEmpty)

  private def chooseWorkflow(): Option[(String, InterpreterWorkflow)] =
    randomSelection(ctx.rctx.workflows, (path, wf) => hasInput(path) && !ctx.suspendingTracker.isSuspended(path))

  private def chooseSequencer(): Option[(String, InterpreterSequencer)] =
    randomSelection(ctx.rctx.sequencers, (path, seqr) => hasInput(path))

  private def chooseSplitter(): Option[(String, InterpreterSplitter)] =
    randomSelection(ctx.rctx.splitters, (path, spltr) => hasInput(path))

  private def chooseGenerator(): Option[(String, InterpreterGenerator)] =
    randomSelection(ctx.rctx.generators, (path, genr) => genr.generator.generator.hasNext())

  private def stepStepper(path: String, stepper: Stepper): StepResult =
    stepper.tpe match
      case StepperType.Processing =>
        val from = ctx.graphTracker.getInputs(path).get.find(from => hasInput(path, from)).get
        val idx = ctx.progressTracker.getProgress(path, from).get
        val inputAtom = ctx.rctx.streams(from).read(idx)
        val outputAtoms = stepper.asInstanceOf[ProcessingStepper].step(inputAtom)
        ctx.progressTracker.incrementProgress(path, from)
        wrapEvents(path, outputAtoms) match
          case r @ Completed(_, _) =>
            r
          case r @ Suspended(_, _, _) =>
            ctx.suspendingTracker.add(path)
            r
      case StepperType.Generating =>
        val outputAtoms = stepper.asInstanceOf[GeneratingStepper].step()
        wrapEvents(path, outputAtoms)

  private def stepPortal(path: String, portal: InterpreterPortal): StepResult =
    portal.dequeue().get match
      // 1) if it is a TestAskBatch, then execute the replying workflow
      case tpa @ AskBatch(meta, _) =>
        val wf = ctx.rctx.workflows(portal.replier)
        val outputAtoms = wf.step(tpa)
        wrapEvents(path, outputAtoms)
      // 2) if it is a TestRepBatch, then execute the asking workflow
      case tpr @ ReplyBatch(meta, _) =>
        val wf = ctx.rctx.workflows(meta.askingWF)
        val outputAtoms = wf.step(tpr)
        wrapEvents(path, outputAtoms)
      case _ => ??? // should not happen

  /** Perform runtime wellformedness checks on the application. */
  private def check(application: Application): Unit =
    RuntimeCompilerPhases.wellFormedCheck(application)(using ctx.rctx)
