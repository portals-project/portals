package portals.benchmark.systems

import scala.collection.immutable.VectorBuilder
import scala.collection.mutable.ArrayDeque
import scala.concurrent.duration.Duration
import scala.concurrent.Await

import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior

import portals.*
import portals.application.generator.Generator
import portals.application.sequencer.Sequencer
import portals.application.task.GenericTask
import portals.application.task.OutputCollector
import portals.application.task.TaskContextImpl
import portals.application.task.TaskExecution
import portals.application.Workflow
import portals.runtime.local.AkkaLocalRuntime
import portals.runtime.local.AkkaRunner
import portals.runtime.local.AkkaRunnerImpl

class MicroBatchingSystem extends AkkaLocalRuntime with PortalsSystem:
  import AkkaRunner.Events.*
  override val runner: AkkaRunner = MicroBatchingRunner

  override def launchWorkflow[T, U](workflow: Workflow[T, U]): Unit =
    val stream = streams(workflow.stream.path)

    var runtimeWorkflow: Map[String, ActorRef[Event[_]]] = Map.empty

    // batching diff
    val batcher = {
      val sinkNames = Set(workflow.sink)
      system.spawnAnonymous(MicroBatchingRunner.batcher("batcher", sinks = sinkNames))
    }
    // end batching diff

    {
      val deps = workflow.connections.filter(_._2 == workflow.sink).map(x => x._1).toSet
      // batching diff
      runtimeWorkflow = runtimeWorkflow + (workflow.sink -> system.spawnAnonymous(
        MicroBatchingRunner.batchingsink(batcher, workflow.sink, Set(stream), deps)
      ))
      // end batching diff
    }

    // here we assume the connections are topologically sorted :)
    workflow.connections.foreach { (from, to) =>
      if !runtimeWorkflow.contains(to) && workflow.tasks.contains(to) then
        val toto = workflow.connections.filter(_._1 == to).map(x => x._2).toSet
        val deps = workflow.connections.filter(_._2 == to).map(x => x._1).toSet
        // batching diff
        val aref = system.spawnAnonymous(
          MicroBatchingRunner.batchingtask[Any, Any](
            to,
            workflow.tasks(to).asInstanceOf[GenericTask[Any, Any, Nothing, Nothing]],
            runtimeWorkflow.filter(x => toto.contains(x._1)).map(_._2).toSet,
            deps
          ),
        )
        // end batching diff
        runtimeWorkflow = runtimeWorkflow + (to -> aref)
    }

    {
      val toto = workflow.connections.filter(_._1 == workflow.source).map(x => x._2)
      // batching diff
      val aref = system.spawnAnonymous(
        MicroBatchingRunner.batchingsource(
          workflow.source,
          runtimeWorkflow.filter(x => toto.contains(x._1)).map(_._2).toSet
        )
      )
      // end batching diff
      runner.connect(streams(workflow.consumes.path), aref)
      runtimeWorkflow = runtimeWorkflow + (workflow.source -> aref)
    }

    // batching diff
    {
      val sourcesNames = Set(workflow.source)
      val sources = runtimeWorkflow.filter(x => sourcesNames.contains(x._1)).map(_._2).toSet
      sources.foreach { s =>
        val fut = batcher.ask(replyTo => MicroBatchingRunner.Events.AddSource(s.asInstanceOf, replyTo))
        Await.result(fut, Duration.Inf)
      }
    }
    // end batching diff

    workflows = workflows + (workflow.path -> runtimeWorkflow)

object MicroBatchingRunner extends AkkaRunner:
  import AkkaRunner.Events.*

  def atomicStream[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[PubSubRequest] =
    AkkaRunnerImpl.atomicStream(path, subscribers)

  def generator[T](path: String, generator: Generator[T], stream: ActorRef[Event[T]]): Behavior[GeneratorCommand] =
    AkkaRunnerImpl.generator(path, generator, stream)

  def sequencer[T](path: String, sequencer: Sequencer[T], stream: ActorRef[Event[T]]): Behavior[Event[T]] =
    AkkaRunnerImpl.sequencer(path, sequencer, stream)

  def source[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[Event[T]] =
    ???

  def sink[T](
      path: String,
      subscribers: Set[ActorRef[Event[T]]] = Set.empty,
      deps: Set[String] = Set.empty
  ): Behavior[Event[T]] =
    ???

  def task[T, U](
      path: String,
      task: GenericTask[T, U, Nothing, Nothing],
      subscribers: Set[ActorRef[Event[U]]] = Set.empty,
      deps: Set[String] = Set.empty
  ): Behavior[Event[T]] =
    ???

  object Events:
    sealed trait BatcherEvents
    case class AddSource(source: ActorRef[SourceCommand[_]], replyTo: ActorRef[BatcherReply]) extends BatcherEvents
    case class BatchComplete(path: String) extends BatcherEvents

    sealed trait BatcherReply
    case object SourceAdded extends BatcherReply

    type SourceCommand[T] = BatchComplete | Event[T]

  import Events.*

  def batchingsource[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[SourceCommand[T]] =
    AtomicSourceExecutor(path, subscribers)

  def batchingsink[T](
      batcher: ActorRef[BatchComplete],
      path: String,
      subscribers: Set[ActorRef[Event[T]]] = Set.empty,
      deps: Set[String] = Set.empty,
  ): Behavior[Event[T]] =
    AtomicSinkExecutor(batcher, path, subscribers, deps)

  def batchingtask[T, U](
      path: String,
      task: GenericTask[T, U, Nothing, Nothing],
      subscribers: Set[ActorRef[Event[U]]] = Set.empty,
      deps: Set[String] = Set.empty
  ): Behavior[Event[T]] =
    AtomicTaskExecutor(path, task, subscribers, deps)

  def batcher(
      path: String,
      sources: Set[ActorRef[Events.SourceCommand[_]]] = Set.empty,
      sinks: Set[String] = Set.empty,
  ): Behavior[BatcherEvents] =
    BatcherExecutor(path, sources, sinks)

  private object BatcherExecutor:
    import Events.*

    def apply(path: String, sources: Set[ActorRef[SourceCommand[_]]], sinks: Set[String]): Behavior[BatcherEvents] =
      // when received all BatchComplete messages from the sinks
      // it will send a BatchComplete message to the sources
      Behaviors.setup { ctx =>
        var received = Set.empty[String]
        Behaviors.receiveMessage {
          case BatchComplete(path) =>
            received += path
            if received.size == sinks.size then
              sources.foreach(_ ! BatchComplete(path))
              received = Set.empty
            Behaviors.same
          case AddSource(source, replyTo) =>
            replyTo ! SourceAdded
            this.apply(path, sources + source, sinks)
        }
      }

  private object AtomicSourceExecutor:
    import Events.*
    def apply[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[SourceCommand[T]] =
      Behaviors.setup { ctx =>
        val vectorBuilder = VectorBuilder[portals.WrappedEvent[T]]()
        val atoms = new ArrayDeque[Vector[WrappedEvent[T]]]()
        var first = true

        Behaviors.receiveMessage {
          case BatchComplete(_) =>
            if !atoms.isEmpty then
              val atom = atoms.head
              atoms.removeHead()
              atom.foreach { e => subscribers.foreach(_ ! Event(path, e)) }
            else first = true
            Behaviors.same
          case Event(sender, event) =>
            event match
              case portals.Event(_, _) =>
                vectorBuilder += event
                Behaviors.same
              case portals.Atom =>
                vectorBuilder += event
                atoms.append(vectorBuilder.result())
                vectorBuilder.clear()
                if first == true then // special case for first atom
                  ctx.self ! BatchComplete(path)
                  first = false
                Behaviors.same
              case portals.Seal =>
                vectorBuilder += event
                atoms.append(vectorBuilder.result())
                vectorBuilder.clear()
                if first == true then // special case for first atom
                  ctx.self ! BatchComplete(path)
                  first = false
                // Behaviors.stopped
                Behaviors.same // we don't stop here as otherwise we might not finish the previous batch
              case portals.Error(t) =>
                vectorBuilder += event
                atoms.append(vectorBuilder.result())
                vectorBuilder.clear()
                throw t
                // Behaviors.stopped
                Behaviors.same // we don't stop here as otherwise we might not finish the previous batch
              case _ => ???
        }
      }

  private object AtomicSinkExecutor:
    import Events.*
    def apply[T](
        batcher: ActorRef[BatchComplete],
        path: String,
        subscribers: Set[ActorRef[Event[T]]] = Set.empty,
        deps: Set[String] = Set.empty
    ): Behavior[Event[T]] =
      Behaviors.setup { ctx =>
        var atomsReceived = Set.empty[String]
        var sealedReceived = Set.empty[String]
        Behaviors.receiveMessage { case Event(sender, event) =>
          event match
            case portals.Event(sender, _) =>
              subscribers.foreach(_ ! Event(path, event))
              Behaviors.same
            case portals.Atom =>
              atomsReceived += sender
              if atomsReceived.size == deps.size then
                batcher ! BatchComplete(path)
                subscribers.foreach(_ ! Event(path, portals.Atom))
                atomsReceived = Set.empty
              Behaviors.same
            case portals.Seal =>
              sealedReceived += sender
              if sealedReceived.size == deps.size then
                batcher ! BatchComplete(path)
                subscribers.foreach(_ ! Event(path, portals.Seal))
                sealedReceived = Set.empty
                Behaviors.stopped
              else Behaviors.same
            case portals.Error(t) =>
              subscribers.foreach(_ ! Event(path, event))
              throw t
              Behaviors.stopped
            case _ => ???
        }
      }

  private object AtomicTaskExecutor:
    def apply[T, U](
        path: String,
        task: GenericTask[T, U, Nothing, Nothing],
        subscribers: Set[ActorRef[Event[U]]] = Set.empty,
        deps: Set[String] = Set.empty
    ): Behavior[Event[T]] =
      Behaviors.setup { ctx =>
        var atomsReceived = Set.empty[String]
        var sealedReceived = Set.empty[String]

        // create task context
        given tctx: TaskContextImpl[T, U, Nothing, Nothing] = TaskContextImpl[T, U, Nothing, Nothing]()
        tctx.outputCollector = new OutputCollector[T, U, Any, Any] {
          def submit(event: WrappedEvent[U]): Unit =
            subscribers.foreach { sub => sub ! Event(path, event) }
          def ask(portal: String, asker: String, req: Any, key: Key[Long], id: Int): Unit = ???
          def reply(r: Any, portal: String, asker: String, key: Key[Long], id: Int): Unit = ???
        }

        val preparedTask = TaskExecution.prepareTask(task, tctx)

        Behaviors.receiveMessage { case Event(sender, event) =>
          event match
            case portals.Event(key, event) =>
              tctx.state.key = key
              tctx.key = key
              preparedTask.onNext(event)
              Behaviors.same
            case portals.Atom =>
              atomsReceived += sender
              if atomsReceived.size == deps.size then
                preparedTask.onAtomComplete
                subscribers.foreach { sub => sub ! Event(path, portals.Atom) }
                atomsReceived = Set.empty
              Behaviors.same
            case portals.Seal =>
              sealedReceived += sender
              if sealedReceived.size == deps.size then
                preparedTask.onComplete
                subscribers.foreach { sub => sub ! Event(path, portals.Seal) }
                Behaviors.stopped
              else Behaviors.same
            case portals.Error(t) =>
              preparedTask.onError(t)
              subscribers.foreach { sub => sub ! Event(path, portals.Error(t)) }
              Behaviors.stopped
            case _ => ???
        }
      }
  end AtomicTaskExecutor
