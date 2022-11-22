package portals.benchmark.systems

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior

import portals.*
import portals.system.parallel.*

class NoGuaranteesSystem extends AkkaLocalSystem:
  import AkkaRunner.Events.*
  override val runner: AkkaRunner = NoGuaranteesRunner

object NoGuaranteesRunner extends AkkaRunner:
  import AkkaRunner.Events.*

  def atomicStream[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[PubSubRequest] =
    AtomicStreamExecutor(path, subscribers)

  def generator[T](path: String, generator: Generator[T], stream: ActorRef[Event[T]]): Behavior[GeneratorCommand] =
    AtomicGeneratorExecutor(path, generator, stream)

  def sequencer[T](path: String, sequencer: Sequencer[T], stream: ActorRef[Event[T]]): Behavior[Event[T]] =
    AtomicSequencerExecutor(path, sequencer, stream)

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
      task: Task[T, U],
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
              case Generator.Event(key, event) =>
                stream ! Event(path, portals.Event[T](key, event))
                Behaviors.same
              case Generator.Atom =>
                cont = false
              case Generator.Seal =>
                cont = false
                stop = true
              case Generator.Error(t) =>
                cont = false
                stop = true
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
        task: Task[T, U],
        subscribers: Set[ActorRef[Event[U]]] = Set.empty,
        deps: Set[String] = Set.empty
    ): Behavior[Event[T]] =
      Behaviors.setup { ctx =>

        // create task context
        given tctx: TaskContextImpl[T, U] = TaskContext[T, U]()
        tctx.cb = new TaskCallback[T, U, Any, Any] {
          def submit(event: WrappedEvent[U]): Unit =
            subscribers.foreach { sub => sub ! Event(path, event) }
          def ask(portal: String, portalAsker: String, replier: String, asker: String, req: Any, key: Key[Int], id: Int)
              : Unit = ???
          def reply(r: Any, portal: String, portalAsker: String, replier: String, asker: String, key: Key[Int], id: Int)
              : Unit = ???
        }

        val preparedTask = Tasks.prepareTask(task, tctx)

        Behaviors.receiveMessage { case Event(_, event) =>
          event match
            case portals.Event(key, event) =>
              tctx.state.key = key
              tctx.key = key
              preparedTask.onNext(event)
              Behaviors.same
            case portals.Atom =>
              preparedTask.onAtomComplete
              Behaviors.same
            case portals.Seal =>
              preparedTask.onComplete
              Behaviors.same
            case portals.Error(t) =>
              preparedTask.onError(t)
              Behaviors.same
            case _ => ???
        }
      }
  end AtomicTaskExecutor
