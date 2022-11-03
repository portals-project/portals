package portals.system.parallel

import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.ActorRef
import akka.util.Timeout

import portals.*

abstract class AkkaLocalSystem extends PortalsSystem:
  import AkkaRunner.Events.*

  given timeout: Timeout = Timeout(3.seconds)
  given system: akka.actor.ActorSystem = akka.actor.ActorSystem("Portals")
  given scheduler: akka.actor.typed.Scheduler = system.toTyped.scheduler

  var streams: Map[String, ActorRef[PubSubRequest]] = Map.empty
  var sequencers: Map[String, ActorRef[Event[_]]] = Map.empty
  var generators: Map[String, ActorRef[GeneratorCommand]] = Map.empty
  var workflows: Map[String, Map[String, ActorRef[Event[_]]]] = Map.empty

  val runner: AkkaRunner

  private[parallel] def launchStream[T](stream: AtomicStream[T]): Unit =
    val aref = system.spawnAnonymous(runner.atomicStream(stream.path))
    streams = streams + (stream.path -> aref)

  private[parallel] def launchSequencer[T](sequencer: AtomicSequencer[T]): Unit =
    val stream = streams(sequencer.stream.path)
    val aref = system.spawnAnonymous(runner.sequencer(sequencer.path, sequencer.sequencer, stream))
    sequencers = sequencers + (sequencer.path -> aref.asInstanceOf[ActorRef[Event[_]]])

  private[parallel] def launchConnection[T](connection: AtomicConnection[T]): Unit =
    runner.connect(streams(connection.from.path), sequencers(connection.to.path))

  private[parallel] def launchGenerator[T](generator: AtomicGenerator[T]): Unit =
    val stream = streams(generator.stream.path)
    val aref =
      system.spawnAnonymous(runner.generator[T](generator.path, generator.generator, stream))
    generators += generator.path -> aref

  private[parallel] def launchWorkflow[T, U](workflow: Workflow[T, U]): Unit =
    val stream = streams(workflow.stream.path)

    var runtimeWorkflow: Map[String, ActorRef[Event[_]]] = Map.empty

    workflow.sinks.foreach { (name, _) =>
      val deps = workflow.connections.filter(_._2 == name).map(x => x._1).toSet
      runtimeWorkflow = runtimeWorkflow + (name -> system.spawnAnonymous(runner.sink(name, Set(stream), deps)))
    }

    // here we assume the connections are topologically sorted :)
    workflow.connections.foreach { (from, to) =>
      if !runtimeWorkflow.contains(to) && workflow.tasks.contains(to) then
        val toto = workflow.connections.filter(_._1 == to).map(x => x._2).toSet
        val deps = workflow.connections.filter(_._2 == to).map(x => x._1).toSet
        val aref = system.spawnAnonymous(
          runner.task[Any, Any](
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
        runner.source(
          name,
          runtimeWorkflow.filter(x => toto.contains(x._1)).map(_._2).toSet
        )
      )
      runner.connect(streams(workflow.consumes.path), aref)
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
