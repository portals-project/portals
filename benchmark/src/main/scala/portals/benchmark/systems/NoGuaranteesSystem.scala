package portals.benchmark.systems

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior

import portals.application.generator.Generator
import portals.application.sequencer.Sequencer
import portals.application.splitter.Splitter
import portals.application.task.GenericTask
import portals.application.task.OutputCollector
import portals.application.task.TaskContextImpl
import portals.runtime.executor.TaskExecutorImpl
import portals.runtime.interpreter.InterpreterEvents.*
import portals.runtime.local.AkkaLocalRuntime
import portals.runtime.local.AkkaRunnerBehaviors
import portals.runtime.WrappedEvents.*
import portals.system.PortalsSystem
import portals.util.Key

class NoGuaranteesSystem extends AkkaLocalRuntime with PortalsSystem:
  import AkkaRunnerBehaviors.Events.*
  override val runner: AkkaRunnerBehaviors = NoGuaranteesRunner

object NoGuaranteesRunner extends AkkaRunnerBehaviors:
  import AkkaRunnerBehaviors.Events.*

  def atomicStream[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[PubSubRequest] =
    AtomicStreamExecutor(path, subscribers)

  def generator[T](path: String, generator: Generator[T], stream: ActorRef[Event[T]]): Behavior[GeneratorCommand] =
    AtomicGeneratorExecutor(path, generator, stream)

  def sequencer[T](path: String, sequencer: Sequencer[T], stream: ActorRef[Event[T]]): Behavior[Event[T]] =
    AtomicSequencerExecutor(path, sequencer, stream)

  def splitter[T](path: String, splitter: Splitter[T]): Behavior[SplitterCommand] = ???

  def source[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[Event[T]] =
    AtomicSourceExecutor(path, subscribers)

  def sink[T](
      path: String,
      subscribers: Set[ActorRef[Event[T]]] = Set.empty,
      deps: Set[String] = Set.empty
  ): Behavior[Event[T]] =
    AtomicSinkExecutor(path, subscribers, deps)

  def task[T, U](
      path: String,
      task: GenericTask[T, U, Nothing, Nothing],
      subscribers: Set[ActorRef[Event[U]]] = Set.empty,
      deps: Set[String] = Set.empty
  ): Behavior[Event[T]] =
    AtomicTaskExecutor(path, task, subscribers, deps)

  private object AtomicStreamExecutor:
    def apply[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[PubSubRequest] =
      Behaviors.receiveMessage {
        case Subscribe(subscriber, replyTo) =>
          replyTo ! SubscribeSuccessfull
          apply(path, subscribers + subscriber)

        case e @ Event(_, _) =>
          subscribers.foreach { _ ! e.asInstanceOf[Event[T]] }
          Behaviors.same
      }
  end AtomicStreamExecutor

  private object AtomicGeneratorExecutor:

    def apply[T](path: String, generator: Generator[T], stream: ActorRef[Event[T]]): Behavior[GeneratorCommand] =
      Behaviors.setup { ctx =>
        ctx.self ! Next
        Behaviors.receiveMessage { case Next =>
          var cont = true
          var stop = false
          while cont && generator.hasNext() do
            generator.generate() match
              case portals.runtime.WrappedEvents.Event(key, event) =>
                stream ! Event(path, portals.runtime.WrappedEvents.Event[T](key, event))
                Behaviors.same
              case portals.runtime.WrappedEvents.Atom =>
                cont = false
              case portals.runtime.WrappedEvents.Seal =>
                cont = false
                stop = true
              case portals.runtime.WrappedEvents.Error(t) =>
                cont = false
                stop = true
              case portals.runtime.WrappedEvents.Ask(_, _, _) => ???
              case portals.runtime.WrappedEvents.Reply(_, _, _) => ???
          if stop == true then Behaviors.stopped
          else
            ctx.self ! Next
            Behaviors.same
        }
      }
  end AtomicGeneratorExecutor

  private object AtomicSequencerExecutor:
    def apply[T](path: String, sequencer: Sequencer[T], stream: ActorRef[Event[T]]): Behavior[Event[T]] =
      Behaviors.receiveMessage[Event[T]] { msg =>
        stream ! msg
        Behaviors.same
      }
  end AtomicSequencerExecutor

  private object AtomicSourceExecutor:
    def apply[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[Event[T]] =
      Behaviors.receiveMessage { case Event(path, event) =>
        subscribers.foreach { _ ! Event(path, event) }
        Behaviors.same
      }
  end AtomicSourceExecutor

  private object AtomicSinkExecutor:
    def apply[T](
        path: String,
        subscribers: Set[ActorRef[Event[T]]] = Set.empty,
        deps: Set[String] = Set.empty
    ): Behavior[Event[T]] =
      Behaviors.receiveMessage { case e @ Event(path, event) =>
        subscribers.foreach { _ ! Event(path, event) }
        Behaviors.same
      }
  end AtomicSinkExecutor

  private object AtomicTaskExecutor:
    def apply[T, U](
        path: String,
        task: GenericTask[T, U, Nothing, Nothing],
        subscribers: Set[ActorRef[Event[U]]] = Set.empty,
        deps: Set[String] = Set.empty
    ): Behavior[Event[T]] =
      Behaviors.setup { ctx =>

        // create task context
        given tctx: TaskContextImpl[T, U, Nothing, Nothing] = TaskContextImpl[T, U, Nothing, Nothing]()
        tctx.outputCollector = new OutputCollector[T, U, Any, Any] {
          def submit(event: WrappedEvent[U]): Unit =
            subscribers.foreach { sub => sub ! Event(path, event) }
          override def ask(portal: String, asker: String, req: Any, key: Key, id: Int, askingWF: String): Unit =
            ???
          override def reply(r: Any, portal: String, asker: String, key: Key, id: Int, askingWF: String): Unit =
            ???
        }

        val preparedTask = TaskExecutorImpl.prepareTask(task, tctx)

        Behaviors.receiveMessage { case Event(_, event) =>
          event match
            case portals.runtime.WrappedEvents.Event(key, event) =>
              tctx.state.key = key
              tctx.key = key
              preparedTask.onNext(event)
              Behaviors.same
            case portals.runtime.WrappedEvents.Atom =>
              preparedTask.onAtomComplete
              Behaviors.same
            case portals.runtime.WrappedEvents.Seal =>
              preparedTask.onComplete
              Behaviors.same
            case portals.runtime.WrappedEvents.Error(t) =>
              preparedTask.onError(t)
              Behaviors.same
            case _ => ???
        }
      }
  end AtomicTaskExecutor
