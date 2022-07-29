package portals

import java.{util => ju}
import java.util.concurrent.Flow.Subscriber
import java.util.LinkedList

import scala.collection.mutable
import scala.util.control.Breaks._

import portals.Generator.GeneratorEvent

trait Recvable:
  def recv(from: AtomicStreamRefKind[_], event: AtomSeq): Unit

trait Executable:
  def isEmpty(): Boolean
  def step(): Unit
  def stepAll(): Unit

case class AtomSeq(val events: List[WrappedEvent[_]])

class SyncLocalSystem extends LocalSystemContext:
  private val logger = Logger("syncLocalSystem")
  val registry = syncRegistry
  val syncRegistry = SyncRegistry()
  // TODO: hanging connection will eventually be empty, check this
  var hangingConnections = Set[AtomicConnection[_]]()

  def launch(application: Application): Unit =
    application.sequencers.foreach {
      syncRegistry.addSequencer(application, _)
    }

    application.generators.foreach(g => syncRegistry.addGenerator(g))

    // launch workflows
    application.workflows.foreach(workflow => syncRegistry.addWorkflow(application, workflow))

    // register workflow to whom it consumes
    application.workflows.foreach(workflow =>
      val runtimeWf = syncRegistry.getWorkflow(workflow.stream).get
      logger.debug(s"${runtimeWf.staticWf.path} subscribes to ${workflow.consumes.path} ")
      addSubscriber(workflow.consumes, runtimeWf)
    )

    var hangingConnectionsToDel = Set[AtomicConnection[_]]()
    hangingConnections.foreach(c => {
      if (syncRegistry.sequencers.contains(c.to.stream.path)) {
        hangingConnectionsToDel += c

        logger.debug(s"${c.to.stream.path} subscribes to ${c.from.path} (hanging)")
        val dstSequencer = syncRegistry.getSequencer(c.to.stream).get
        dstSequencer.subscribe(c.from) // sequencer's aspect
        addSubscriber(c.from, dstSequencer) // upStream's aspect
      }
    })
    hangingConnections --= hangingConnectionsToDel

    application.connections.foreach(c =>
      if (syncRegistry.sequencers.contains(c.to.stream.path)) {
        logger.debug(s"${c.to.stream.path} subscribes to ${c.from.path} ")
        val dstSequencer = syncRegistry.getSequencer(c.to.stream).get
        dstSequencer.subscribe(c.from)
        addSubscriber(c.from, dstSequencer)
      } else {
        hangingConnections += c
      }
    )

  def addSubscriber(from: AtomicStreamRefKind[_], to: Recvable): Unit = {
    syncRegistry.getWorkflow(from) match {
      case Some(wf) => wf.subscribers = wf.subscribers :+ to
      case None =>
        syncRegistry.getSequencer(from) match {
          case Some(seq) => seq.subscribers += (from -> to)
          case None =>
            syncRegistry.getGenerator(from) match {
              case Some(gen) => gen.subscribers = gen.subscribers :+ to
              case None => throw new Exception("Unknown consumer: " + from.path)
            }
        }
    }
  }

  var executionIndex = 0
  override def step(): Unit = {
    // get one atom from either generator or sequencer

    // first check if we can get an atom from sequencers
    var executableSequencers = syncRegistry.sequencers.filter(!_._2.isEmpty()).values.toList
    if (executableSequencers.size > 0) {
      val selectedSequencer = executableSequencers(executionIndex % executableSequencers.size)
      logger.debug(s"Executing sequencer ${selectedSequencer.staticSequencer.path}")
      selectedSequencer.step()
    } else {
      // else get one atom from generator
      val executableGenerators = syncRegistry.generators.filter(!_._2.isEmpty()).values.toList
      if (executableGenerators.size == 0) { throw new Exception("No generator to execute, should check isEmpty first") }
      val selectedGenerator = executableGenerators(executionIndex % executableGenerators.size)
      logger.debug(s"Executing generator ${selectedGenerator.g.path}")
      selectedGenerator.step()

      // if still no workflow to execute, meaning this atom is at the sequencer now
      if (syncRegistry.workflows.filter(!_._2.isEmpty()).values.toList.size == 0) {
        executableSequencers = syncRegistry.sequencers.filter(!_._2.isEmpty()).values.toList
        val selectedSequencer = executableSequencers(executionIndex % executableSequencers.size)
        logger.debug(s"Executing sequencer ${selectedSequencer.staticSequencer.path}")
        selectedSequencer.step()
      }
    }

    var executableWorkflows = syncRegistry.workflows.filter(!_._2.isEmpty()).values.toList
    if (executableWorkflows.size == 0) { throw new Exception("No workflow to execute, should not happen") }

    // execute until no workflow is executable
    while (executableWorkflows.size > 0) {
      val selectedWorkflow = executableWorkflows(executionIndex % executableWorkflows.size)
      logger.debug(s"Executing workflow ${selectedWorkflow.staticWf.path}")
      selectedWorkflow.step()
      executableWorkflows = syncRegistry.workflows.filter(!_._2.isEmpty()).values.toList
    }

    executionIndex += 1
  }

  def stepAll(): Unit = {
    while (!isEmpty()) step()
  }
  override def isEmpty(): Boolean =
    syncRegistry.generators.values.forall(_.isEmpty()) && syncRegistry.sequencers.values.forall(_.isEmpty())

  def shutdown(): Unit = ()
