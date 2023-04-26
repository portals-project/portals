package portals.runtime.local

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.ActorRef
import akka.util.Timeout

import com.typesafe.config.ConfigFactory

import portals.application.*
import portals.application.task.GenericTask
import portals.runtime.PortalsRuntime

abstract class AkkaLocalRuntime extends PortalsRuntime:
  import AkkaRunnerBehaviors.Events.*

  private val parallelism: Option[Int] = None

  // see https://doc.akka.io/docs/akka/current/coordinated-shutdown.html
  private val defaultConfig = ConfigFactory
    .parseString(
      s"""
      akka.log-dead-letters-during-shutdown = off
      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-actor-system-terminate = off
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      akka.cluster.run-coordinated-shutdown-when-down = off
      """
    )

  private val cf =
    if parallelism.isDefined then
      ConfigFactory
        .parseString(
          s"""
          akka.actor.default-dispatcher.fork-join-executor.parallelism-min = ${parallelism.get}
          akka.actor.default-dispatcher.fork-join-executor.parallelism-max = ${parallelism.get}
          akka.actor.default-dispatcher.fork-join-executor.parallelism-factor = 1.0
          """
        )
        .withFallback(defaultConfig)
    else defaultConfig

  given timeout: Timeout = Timeout(3.seconds)
  given system: akka.actor.ActorSystem = akka.actor.ActorSystem("Portals", cf)
  given scheduler: akka.actor.typed.Scheduler = system.toTyped.scheduler

  var streams: Map[String, ActorRef[PubSubRequest]] = Map.empty
  var splitters: Map[String, ActorRef[Any]] = Map.empty
  var sequencers: Map[String, ActorRef[Event[_]]] = Map.empty
  var generators: Map[String, ActorRef[GeneratorCommand]] = Map.empty
  var workflows: Map[String, Map[String, ActorRef[Event[_]]]] = Map.empty

  val runner: AkkaRunnerBehaviors

  def launch(application: Application): Unit =

    // first launch all streams
    application.streams.foreach { stream => launchStream(stream) }

    // then launch all splitters
    application.splitters.foreach { splitter => launchSplitter(splitter) }

    // then launch all splits
    application.splits.foreach { split => launchSplit(split) }

    // then launch all sequencers
    application.sequencers.foreach { sequencer => launchSequencer(sequencer) }

    // then launch all connections
    application.connections.foreach { connection => launchConnection(connection) }

    // then launch all workflows
    application.workflows.foreach { workflow => launchWorkflow(workflow) }

    // then launch all generators
    application.generators.foreach { generator => launchGenerator(generator) }

  def shutdown(): Unit =
    Await.result(system.terminate(), 5.seconds)

  private[portals] def launchStream[T](stream: AtomicStream[T]): Unit =
    val aref = system.spawnAnonymous(runner.atomicStream(stream.path))
    streams = streams + (stream.path -> aref)

  private[portals] def launchSplitter[T](splitter: AtomicSplitter[T]): Unit =
    val aref = system.spawnAnonymous(runner.splitter(splitter.path, splitter.splitter))
    splitters = splitters + (splitter.path -> aref.asInstanceOf[ActorRef[Any]])
    val fromStream = streams(splitter.in.path)
    val fut = fromStream.ask(replyTo => Subscribe(aref, replyTo))
    Await.result(fut, Duration.Inf)

  private[portals] def launchSplit[T](split: AtomicSplit[T]): Unit =
    val splitterPath = split.from.path
    val splitter = splitters(splitterPath)
    val streamPath = split.to.path
    val stream = streams(streamPath)
    val filter = split.filter
    val fut = splitter.ask(replyTo => SplitterSubscribe(streamPath, stream, split.filter, replyTo))
    Await.result(fut, Duration.Inf)

  private[portals] def launchSequencer[T](sequencer: AtomicSequencer[T]): Unit =
    val stream = streams(sequencer.stream.path)
    val aref = system.spawnAnonymous(runner.sequencer(sequencer.path, sequencer.sequencer, stream))
    sequencers = sequencers + (sequencer.path -> aref.asInstanceOf[ActorRef[Event[_]]])

  private[portals] def launchConnection[T](connection: AtomicConnection[T]): Unit =
    runner.connect(streams(connection.from.path), sequencers(connection.to.path))

  private[portals] def launchGenerator[T](generator: AtomicGenerator[T]): Unit =
    val stream = streams(generator.stream.path)
    val aref = system.spawnAnonymous(runner.generator(generator.path, generator.generator, stream))
    generators += generator.path -> aref

  private[portals] def launchWorkflow[T, U](workflow: Workflow[T, U]): Unit =
    val stream = streams(workflow.stream.path)

    var runtimeWorkflow: Map[String, ActorRef[Event[_]]] = Map.empty

    // sink
    {
      val path = workflow.sink
      val deps = workflow.connections.filter(_._2 == path).map(x => x._1).toSet
      val aref = system.spawnAnonymous(runner.sink(path, Set(stream), deps))
      runtimeWorkflow = runtimeWorkflow + (path -> aref)
    }

    // tasks
    {
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
    }

    // source
    {
      val path = workflow.source
      val toto = workflow.connections.filter(_._1 == path).map(x => x._2)
      val aref = system.spawnAnonymous(
        runner.source(
          path,
          runtimeWorkflow.filter(x => toto.contains(x._1)).map(_._2).toSet,
        )
      )
      runner.connect(streams(workflow.consumes.path), aref)
      runtimeWorkflow = runtimeWorkflow + (path -> aref)
    }

    // workflow
    workflows = workflows + (workflow.path -> runtimeWorkflow)
