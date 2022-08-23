package portals.system.async

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.util.Timeout

import com.typesafe.config.ConfigFactory

import portals.*
import portals.system.async.AtomicGeneratorExecutor.GeneratorCommand
import portals.system.async.Events.Command
import portals.system.async.Events.Subscribe
import portals.SystemContext

class AsyncLocalSystemWithoutAlignment extends AsyncLocalSystem:
  import Events.*

  override def launchSequencer[T](sequencer: AtomicSequencer[T]): Unit =
    val stream = streams(sequencer.stream.path)
    val aref =
      system.spawnAnonymous(WithoutAlignment.AtomicSequencerExecutor(sequencer.path, sequencer.sequencer, stream))
    sequencers = sequencers + (sequencer.path -> aref)

  override def launchWorkflow[T, U](workflow: Workflow[T, U]): Unit =
    val stream = streams(workflow.stream.path)

    var runtimeWorkflow: Map[String, ActorRef[Command]] = Map.empty

    workflow.sinks.foreach { (name, _) =>
      val deps = workflow.connections.filter(_._2 == name).map(x => x._1).toSet
      runtimeWorkflow =
        runtimeWorkflow + (name -> system.spawnAnonymous(WithoutAlignment.Sink(name, Set(stream), deps)))
    }

    // here we assume the connections are topologically sorted :)
    workflow.connections.foreach { (from, to) =>
      if !runtimeWorkflow.contains(to) && workflow.tasks.contains(to) then
        val toto = workflow.connections.filter(_._1 == to).map(x => x._2).toSet
        val deps = workflow.connections.filter(_._2 == to).map(x => x._1).toSet
        val aref = system.spawnAnonymous(
          WithoutAlignment.Task[Any, Any](
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

object WithoutAlignment:
  // Sink, implemented as a Task for now, as it also uses alignment.
  object Sink:
    def apply(
        path: String,
        subscribers: Set[ActorRef[Command]] = Set.empty,
        deps: Set[String] = Set.empty
    ): Behavior[Command] =
      Task(path, Tasks.identity, subscribers, deps)

  // Task Without Alignment
  object Task:
    def apply[T, U](
        path: String,
        task: Task[T, U],
        subscribers: Set[ActorRef[Command]] = Set.empty,
        deps: Set[String] = Set.empty,
    ): Behavior[Command] =
      // atom alignment
      Behaviors.setup { ctx =>

        // create task context
        given tctx: TaskContextImpl[T, U] = TaskContext[T, U]()
        tctx.cb = new TaskCallback[T, U] {
          def submit(key: Key[Int], event: U): Unit =
            subscribers.foreach { sub => sub ! Events.Event(path, portals.Event(tctx.key, event)) }
          def fuse(): Unit = () // do nothing, for now, but deprecated, remove it.
        }

        Behaviors.receiveMessage {
          case Subscribe(_, _) => ??? // not supported

          case Events.Event(sender, event) =>
            event match
              case portals.Event(key, e) =>
                tctx.state.key = key
                tctx.key = key
                task.onNext(e.asInstanceOf)
                Behaviors.same
              case portals.Atom =>
                task.onAtomComplete
                Behaviors.same
              case portals.Seal =>
                task.onComplete
                Behaviors.stopped
              case portals.Error(t) =>
                task.onError(t)
                throw t
                Behaviors.stopped
        }
      }

  object AtomicSequencerExecutor:
    import scala.collection.mutable.Map

    import Events.*

    /** Sequencer Executor.
      */
    def apply[T](path: String, sequencer: Sequencer[T], stream: ActorRef[Command]): Behavior[Command] =
      Behaviors.setup { ctx =>
        Behaviors.receiveMessage {
          case Subscribe(_, _) => ??? // not supported

          case Event(sender, event): Event[T] =>
            stream ! Events.Event(path, event)
            Behaviors.same
        }
      }
  end AtomicSequencerExecutor // object
