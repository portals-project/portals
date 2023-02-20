package portals.runtime.local

import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.ActorRef
import akka.util.Timeout

import com.typesafe.config.ConfigFactory

import portals.*

abstract class AkkaLocalRuntime extends PortalsRuntime:
  import AkkaRunner.Events.*

  val cf = ConfigFactory
    .parseString(
      s"""
      akka.log-dead-letters-during-shutdown = off
      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-actor-system-terminate = off
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      akka.cluster.run-coordinated-shutdown-when-down = off
      """
    )
    .withFallback(ConfigFactory.defaultApplication)

  // options for setting parallelism for Akka.
  // actor.default-dispatcher.fork-join-executor.parallelism-min = ${parallelism}
  // actor.default-dispatcher.fork-join-executor.parallelism-max = ${parallelism}
  // actor.default-dispatcher.fork-join-executor.parallelism-factor = 1.0

  given timeout: Timeout = Timeout(3.seconds)
  given system: akka.actor.ActorSystem = akka.actor.ActorSystem("Portals", cf)
  given scheduler: akka.actor.typed.Scheduler = system.toTyped.scheduler

  var streams: Map[String, ActorRef[PubSubRequest]] = Map.empty
  var sequencers: Map[String, ActorRef[Event[_]]] = Map.empty
  var generators: Map[String, ActorRef[GeneratorCommand]] = Map.empty
  var workflows: Map[String, Map[String, ActorRef[Event[_]]]] = Map.empty

  val runner: AkkaRunner

  private[portals] def launchStream[T](stream: AtomicStream[T]): Unit =
    val aref = system.spawnAnonymous(runner.atomicStream(stream.path))
    streams = streams + (stream.path -> aref)

  private[portals] def launchSequencer[T](sequencer: AtomicSequencer[T]): Unit =
    val stream = streams(sequencer.stream.path)
    val aref = system.spawnAnonymous(runner.sequencer(sequencer.path, sequencer.sequencer, stream))
    sequencers = sequencers + (sequencer.path -> aref.asInstanceOf[ActorRef[Event[_]]])

  private[portals] def launchConnection[T](connection: AtomicConnection[T]): Unit =
    runner.connect(streams(connection.from.path), sequencers(connection.to.path))

  private[portals] def launchGenerator[T](generator: AtomicGenerator[T]): Unit =
    val stream = streams(generator.stream.path)
    val aref =
      system.spawnAnonymous(runner.generator[T](generator.path, generator.generator, stream))
    generators += generator.path -> aref

  private[portals] def launchWorkflow[T, U](workflow: Workflow[T, U]): Unit =
    val stream = streams(workflow.stream.path)

    var runtimeWorkflow: Map[String, ActorRef[Event[_]]] = Map.empty

    { // remnant from when we had multiple sinks in a workflow, can probably do this differently now :).
      val deps = workflow.connections.filter(_._2 == workflow.sink).map(x => x._1).toSet
      runtimeWorkflow =
        runtimeWorkflow + (workflow.sink -> system.spawnAnonymous(runner.sink(workflow.sink, Set(stream), deps)))
    }

    // here we assume the connections are topologically sorted :)
    workflow.connections.foreach { (from, to) =>
      if !runtimeWorkflow.contains(to) && workflow.tasks.contains(to) then
        val toto = workflow.connections.filter(_._1 == to).map(x => x._2).toSet
        val deps = workflow.connections.filter(_._2 == to).map(x => x._1).toSet
        val aref = system.spawnAnonymous(
          runner.task[Any, Any](
            to,
            workflow.tasks(to).asInstanceOf[GenericTask[Any, Any, Nothing, Nothing]],
            runtimeWorkflow.filter(x => toto.contains(x._1)).map(_._2).toSet,
            deps
          ),
        )
        runtimeWorkflow = runtimeWorkflow + (to -> aref)
    }

    { // remnant from when we had multiple sources in a workflow, can probably do this differently now :).
      val toto = workflow.connections.filter(_._1 == workflow.source).map(x => x._2)
      val aref = system.spawnAnonymous(
        runner.source(
          workflow.source,
          runtimeWorkflow.filter(x => toto.contains(x._1)).map(_._2).toSet
        )
      )
      runner.connect(streams(workflow.consumes.path), aref)
      runtimeWorkflow = runtimeWorkflow + (workflow.source -> aref)
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