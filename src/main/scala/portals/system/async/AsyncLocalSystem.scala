package portals.system.async

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.ActorRef
import akka.util.Timeout

import com.typesafe.config.ConfigFactory

import portals.*
import portals.system.async.AtomicGeneratorExecutor.GeneratorCommand
import portals.SystemContext

class AsyncLocalSystem extends SystemContext:
  import Events.*

  given timeout: Timeout = Timeout(3.seconds)
  given system: akka.actor.ActorSystem = akka.actor.ActorSystem("Portals")
  given scheduler: akka.actor.typed.Scheduler = system.toTyped.scheduler

  var streams: Map[String, ActorRef[Command]] = Map.empty
  var sequencers: Map[String, ActorRef[Command]] = Map.empty
  var generators: Map[String, ActorRef[GeneratorCommand]] = Map.empty
  var workflows: Map[String, Map[String, ActorRef[Command]]] = Map.empty

  val registry: GlobalRegistry = null

  private[AsyncLocalSystem] def launchStream[T](stream: AtomicStream[T]): Unit =
    val aref = system.spawnAnonymous(AtomicStreamExecutor(stream.path))
    streams = streams + (stream.path -> aref)

  private[AsyncLocalSystem] def launchSequencer[T](sequencer: AtomicSequencer[T]): Unit =
    val stream = streams(sequencer.stream.path)
    val aref = system.spawnAnonymous(AtomicSequencerExecutor(sequencer.path, sequencer.sequencer, stream))
    sequencers = sequencers + (sequencer.path -> aref)

  private[AsyncLocalSystem] def launchConnection[T](connection: AtomicConnection[T]): Unit =
    AtomicConnectionsExecutor.connect(streams(connection.from.path), sequencers(connection.to.path))

  private[AsyncLocalSystem] def launchGenerator[T](generator: AtomicGenerator[T]): Unit =
    val stream = streams(generator.stream.path)
    val aref =
      system.spawnAnonymous(AtomicGeneratorExecutor[T](generator.path, generator.generator, stream))
    generators += generator.path -> aref

  private[AsyncLocalSystem] def launchWorkflow[T, U](workflow: Workflow[T, U]): Unit =
    val stream = streams(workflow.stream.path)

    var runtimeWorkflow: Map[String, ActorRef[Command]] = Map.empty

    workflow.sinks.foreach { (name, _) =>
      val deps = workflow.connections.filter(_._2 == name).map(x => x._1).toSet
      runtimeWorkflow =
        runtimeWorkflow + (name -> system.spawnAnonymous(AtomicWorkflowExecutor.Sink(name, Set(stream), deps)))
    }

    // here we assume the connections are topologically sorted :)
    workflow.connections.foreach { (from, to) =>
      if !runtimeWorkflow.contains(to) && workflow.tasks.contains(to) then
        val toto = workflow.connections.filter(_._1 == to).map(x => x._2).toSet
        val deps = workflow.connections.filter(_._2 == to).map(x => x._1).toSet
        val aref = system.spawnAnonymous(
          AtomicWorkflowExecutor.Task[Any, Any](
            to,
            workflow.tasks(to).asInstanceOf[Task[Any, Any]],
            runtimeWorkflow.filter(x => toto.contains(x._1)).map(_._2).toSet,
            deps
          ),
        )
        runtimeWorkflow = runtimeWorkflow + (to -> aref)
    }
    workflow.sources.foreach { (name, t) =>
      val toto = workflow.connections.filter(_._1 == name).map(x => x._2)
      val aref = system.spawnAnonymous(
        AtomicWorkflowExecutor.Source(
          name,
          runtimeWorkflow.filter(x => toto.contains(x._1)).map(_._2).toSet
        )
      )
      AtomicConnectionsExecutor.connect(streams(workflow.consumes.path), aref)
      runtimeWorkflow = runtimeWorkflow + (name -> aref)
    }
    workflows = workflows + (workflow.path -> runtimeWorkflow)

  def launch(application: Application): Unit =

    // first launch all streams
    application.streams.foreach { stream => launchStream(stream) }

    // then launch all sequencers
    application.sequencers.foreach { sequencer => launchSequencer(sequencer) }

    // launch all connections
    application.connections.foreach { connection => launchConnection(connection) }

    // then launch all workflows
    application.workflows.foreach { workflow => launchWorkflow(workflow) }

    // then launch all generators
    application.generators.foreach { generator => launchGenerator(generator) }

  def shutdown(): Unit =
    Await.result(system.terminate(), 5.seconds)
