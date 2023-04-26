package portals.runtime.local

import scala.collection.immutable.VectorBuilder

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior

import portals.api.builder.TaskBuilder
import portals.application.generator.Generator
import portals.application.sequencer.Sequencer
import portals.application.splitter.Splitter
import portals.application.task.GenericTask
import portals.application.task.OutputCollector
import portals.application.task.TaskContextImpl
// import portals.runtime.executor.AlignmentExecutorImpl
import portals.runtime.executor.GeneratorExecutorImpl
import portals.runtime.executor.SequencerExecutorImpl
import portals.runtime.executor.SplitterExecutorImpl
import portals.runtime.executor.TaskExecutorImpl
import portals.runtime.interpreter.InterpreterEvents.*
import portals.runtime.local.EagerTaskExecutorImpl
import portals.runtime.WrappedEvents.*
import portals.util.Key

object AkkaRunnerImpl extends AkkaRunnerBehaviors:
  import AkkaRunnerBehaviors.Events.*

  //////////////////////////////////////////////////////////////////////////////
  // Behavior Factories
  //////////////////////////////////////////////////////////////////////////////

  override def atomicStream[T](
      path: String,
      subscribers: Set[ActorRef[Event[T]]] = Set.empty
  ): Behavior[PubSubRequest] =
    AtomicStreamExecutor(path, subscribers)

  override def generator[T](
      path: String,
      generator: Generator[T],
      stream: ActorRef[Event[T]]
  ): Behavior[GeneratorCommand] =
    AtomicGeneratorExecutor(path, generator, stream)

  override def sequencer[T](path: String, sequencer: Sequencer[T], stream: ActorRef[Event[T]]): Behavior[Event[T]] =
    AtomicSequencerExecutor[T](path, sequencer, stream)

  override def splitter[T](path: String, splitter: Splitter[T]): Behavior[SplitterCommand] =
    AtomicSplitterExecutor[T](path, splitter, Map.empty)

  override def source[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[Event[T]] =
    AtomicWorkflowExecutor.Source(path, subscribers)

  override def sink[T](
      path: String,
      subscribers: Set[ActorRef[Event[T]]] = Set.empty,
      deps: Set[String] = Set.empty
  ): Behavior[Event[T]] =
    AtomicWorkflowExecutor.Sink[T](path, subscribers, deps)

  override def task[T, U](
      path: String,
      task: GenericTask[T, U, Nothing, Nothing],
      subscribers: Set[ActorRef[Event[U]]] = Set.empty,
      deps: Set[String] = Set.empty
  ): Behavior[Event[T]] =
    AtomicWorkflowExecutor.Task(path, task.asInstanceOf, subscribers, deps)

  //////////////////////////////////////////////////////////////////////////////
  // Executors
  //////////////////////////////////////////////////////////////////////////////

  // Behavior executor for an atomic stream.
  private object AtomicStreamExecutor:
    def apply[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[PubSubRequest] =
      // Atomic stream with `path`, that implements a PubSub protocol. Forwards all events to subs.
      Behaviors.setup { ctx =>
        var subs = subscribers

        Behaviors.receiveMessage {
          case Subscribe(subscriber, replyTo) =>
            subs = subs + subscriber
            replyTo ! SubscribeSuccessfull
            Behaviors.same

          case Event(_, event) =>
            subs.foreach { _ ! Event(path, event.asInstanceOf[WrappedEvent[T]]) }
            Behaviors.same
        }
      }
  end AtomicStreamExecutor

  // Behavior executor for an atomic generator.
  private object AtomicGeneratorExecutor:
    def apply[T](path: String, generator: Generator[T], stream: ActorRef[Event[T]]): Behavior[GeneratorCommand] =
      Behaviors.setup { ctx =>

        // setup the generator
        val generatorExecutor = new GeneratorExecutorImpl[T]()
        generatorExecutor.setup(path, generator)

        // start generating
        ctx.self ! Next

        Behaviors.receiveMessage { case Next =>
          // try to build an atom
          generatorExecutor.run() match
            case Some(vector) =>
              // if a full atom, then emit events and continue
              if generatorExecutor.atom then
                vector.foreach { stream ! Event(path, _) }
                ctx.self ! Next
                Behaviors.same
              // else if sealed or error, then emit events and stop
              else if generatorExecutor.seal || generatorExecutor.error then
                vector.foreach { stream ! Event(path, _) }
                Behaviors.stopped
              // should not happen
              else ???
            case None =>
              // try again
              ctx.self ! Next
              Behaviors.same
        }
      }
  end AtomicGeneratorExecutor

  // Behavior executor for an atomic sequencer.
  private object AtomicSequencerExecutor:
    // def coordinator()
    def apply[T](path: String, sequencer: Sequencer[T], stream: ActorRef[Event[T]]): Behavior[Event[T]] =
      Behaviors.setup { ctx =>

        // setup the sequencer
        val sequencerExecutor = new SequencerExecutorImpl[T]()
        sequencerExecutor.setup(path, sequencer)

        Behaviors.receiveMessage { event =>
          // try to sequence an atom by running the sequencer on the event
          sequencerExecutor.run(event) match
            // if the sequencer sequenced an atom, emit the sequenced atom
            case Some(vector) =>
              vector.foreach { e => stream ! Event(path, e) }
              Behaviors.same
            // if the sequencer did nothing, do nothing
            case None =>
              Behaviors.same
        }
      }
  end AtomicSequencerExecutor

  // Behavior executor for an atomic splitter.
  private object AtomicSplitterExecutor:

    def apply[T](
        path: String,
        splitter: Splitter[T],
        subscribers: Map[String, ActorRef[Event[T]]] = Map.empty
    ): Behavior[SplitterCommand] =
      Behaviors.setup { ctx =>
        // setup the subs
        var subs = subscribers

        // setup the splitter
        val splitterExecutor = new SplitterExecutorImpl[T]()
        splitterExecutor.setup(path, splitter)

        Behaviors.receiveMessage {
          case SplitterSubscribe(path, subscriber, filter, replyTo) =>
            subs = subs + (path -> subscriber.asInstanceOf[ActorRef[Event[T]]])
            splitterExecutor.addOutput(path, filter.asInstanceOf[T => Boolean])
            replyTo ! SplitterSubscribeSuccessfull
            Behaviors.same

          case Event(_, event) =>
            splitterExecutor.split(List(event.asInstanceOf[WrappedEvent[T]])) match
              case l =>
                if l.isEmpty then // atom or similar, then broadcast, hack :)
                  subs.values.foreach { _ ! Event(path, event.asInstanceOf[WrappedEvent[T]]) }
                else
                  l.foreach { case (path, we) =>
                    subs.get(path).get ! Event(path, we)
                  }
                Behaviors.same
        }
      }

  end AtomicSplitterExecutor

  private object AtomicWorkflowExecutor:
    // The source simply forwards events to the subscribers.
    // No alignment is needed as it listens only to a single stream.
    object Source:
      def apply[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[Event[T]] =
        Behaviors.setup { ctx =>
          Behaviors.receiveMessage { case Event(sender, event) =>
            subscribers.foreach { _ ! Event(path, event) }
            Behaviors.same
          }
        }

    // Sink, implemented as a Task for now, as it also uses alignment.
    object Sink:
      def apply[T](
          path: String,
          subscribers: Set[ActorRef[Event[T]]] = Set.empty,
          deps: Set[String] = Set.empty
      ): Behavior[Event[T]] =
        Task[T, T](path, TaskBuilder.identity.asInstanceOf, subscribers, deps)

    // Task
    object Task:
      def apply[T, U](
          path: String,
          task: GenericTask[T, U, Nothing, Nothing],
          subscribers: Set[ActorRef[Event[U]]] = Set.empty,
          deps: Set[String] = Set.empty,
          wfPath: String = "",
      ): Behavior[Event[T]] =
        // atom alignment
        Behaviors.setup { ctx =>
          // setup

          // task executor
          val taskExecutor = new EagerTaskExecutorImpl()
          val preparedTask = taskExecutor.prepareTask(task)
          taskExecutor.setup(path, wfPath, preparedTask.asInstanceOf, subscribers.asInstanceOf)

          // alignment executor
          val alignmentExecutor = new Alignment[T]()
          alignmentExecutor.setup(deps.toSeq)

          def send_all(e: WrappedEvent[Any]): Unit =
            subscribers.foreach { _ ! Event(path, e.asInstanceOf) }

          //////////////////////////////////////////////////////////////////////
          // Message Handler
          //////////////////////////////////////////////////////////////////////
          import Alignment.*

          Behaviors.receiveMessage { event =>
            alignmentExecutor.receiveMessage(event) match
              case BatchMessage(seq) =>
                seq.foreach { e =>
                  taskExecutor.run_event(e)
                }
                Behaviors.same

              case EventMessage(e) =>
                taskExecutor.run_event(e)
                Behaviors.same

              case SealedMessage(e) =>
                taskExecutor.run_event(e)
                Behaviors.stopped

              case ErroredMessage(e) =>
                taskExecutor.run_event(e)
                Behaviors.stopped

              case NoMessage =>
                Behaviors.same
          }
        }
