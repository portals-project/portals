package portals.runtime.local

import scala.collection.immutable.VectorBuilder

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior

import portals.api.builder.TaskBuilder
import portals.application.generator.Generator
import portals.application.sequencer.Sequencer
import portals.application.task.GenericTask
import portals.application.task.OutputCollector
import portals.application.task.TaskContextImpl
import portals.application.task.TaskExecution
import portals.runtime.interpreter.InterpreterEvents.*
import portals.runtime.WrappedEvents.*
import portals.util.Key

object AkkaRunnerImpl extends AkkaRunner:
  import AkkaRunner.Events.*

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

  private object AtomicStreamExecutor:
    def apply[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[PubSubRequest] =
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

  private object AtomicGeneratorExecutor:
    def apply[T](path: String, generator: Generator[T], stream: ActorRef[Event[T]]): Behavior[GeneratorCommand] =
      Behaviors.setup { ctx =>

        val vectorBuilder = VectorBuilder[portals.runtime.WrappedEvents.WrappedEvent[T]]()
        var atom = false
        var error = false
        var seal = false
        def go = !atom && !error && !seal

        ctx.self ! Next

        Behaviors.receiveMessage { case Next =>
          while go && generator.hasNext() do
            generator.generate() match
              case portals.runtime.WrappedEvents.Event(key, event) =>
                vectorBuilder += portals.runtime.WrappedEvents.Event(key, event)
              case portals.runtime.WrappedEvents.Atom =>
                vectorBuilder += portals.runtime.WrappedEvents.Atom
                atom = true
              case portals.runtime.WrappedEvents.Seal =>
                vectorBuilder.clear()
                vectorBuilder += portals.runtime.WrappedEvents.Seal
                seal = true
              case portals.runtime.WrappedEvents.Error(t) =>
                vectorBuilder.clear()
                vectorBuilder += portals.runtime.WrappedEvents.Error(t)
                error = true
              case _ => ???

          // if full atom, then send atom to stream and continue, else stop
          if atom == true then
            vectorBuilder.result().foreach { stream ! Event(path, _) }
            vectorBuilder.clear()
            atom = false
            ctx.self ! Next
            Behaviors.same

          // else if sealed then forward seal and stop
          else if seal == true then
            vectorBuilder.result().foreach { stream ! Event(path, _) }
            vectorBuilder.clear()
            Behaviors.stopped

          // else if error then forward error and stop
          else if error == true then
            vectorBuilder.result().foreach { stream ! Event(path, _) }
            vectorBuilder.clear()
            Behaviors.stopped

          // if generator is finished, then continue
          else if !generator.hasNext() then
            ctx.self ! Next
            Behaviors.same

          // otherwise try again
          else
            ctx.self ! Next
            Behaviors.same
        }
      }
  end AtomicGeneratorExecutor

  private object AtomicSequencerExecutor:
    import scala.collection.mutable.Map
    def apply[T](path: String, sequencer: Sequencer[T], stream: ActorRef[Event[T]]): Behavior[Event[T]] =
      Behaviors.setup { ctx =>

        val atoms: Map[String, Vector[Vector[WrappedEvent[T]]]] = Map.empty
        val buffers: Map[String, VectorBuilder[WrappedEvent[T]]] = Map.empty

        Behaviors.receiveMessage { case Event(sender, event) =>
          // add event to buffer
          buffers += sender -> buffers.getOrElse(sender, VectorBuilder[WrappedEvent[T]]()).addOne(event)

          event match
            case Atom =>
              // the sequencer discards any empty atoms
              if buffers(sender).result().length == 1 then
                buffers(sender).clear()
                Behaviors.same
              else
                // add atom to atoms
                atoms += sender -> atoms.getOrElse(sender, Vector.empty).appended(buffers(sender).result())
                // clear buffer
                buffers(sender).clear()

                // get the sequencers choice
                // TODO: change the way the sequencer choses the next event.
                sequencer.sequence(atoms.keys.toList*) match
                  case None => Behaviors.same // no choice was made

                  case Some(choice) =>
                    val sender = choice
                    atoms(sender).head.foreach { e => stream ! Event(path, e) }
                    atoms += sender -> atoms(sender).tail
                    if atoms(sender).isEmpty then atoms -= sender
                    Behaviors.same

            case _ => Behaviors.same // do nothing
        }
      }
  end AtomicSequencerExecutor

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
      ): Behavior[Event[T]] =
        // atom alignment
        Behaviors.setup { ctx =>

          // set of blocking and non-blocking incoming streams
          var blocking = Set.empty[String]
          var nonBlocking = Set.empty[String]

          // set of sealed and non-sealed incoming streams
          var seald = Set.empty[String] // intentionally not sealed, as reserved Scala keyword
          var nonSeald = Set.empty[String]

          // create task context
          given tctx: TaskContextImpl[T, U, Nothing, Nothing] = TaskContextImpl[T, U, Nothing, Nothing]()
          tctx.outputCollector = new OutputCollector[T, U, Any, Any] {
            def submit(event: WrappedEvent[U]): Unit =
              subscribers.foreach { sub => sub ! Event(path, event) }

            // Asker Task
            def ask(
                portal: String,
                asker: String,
                req: Any,
                key: Key[Long],
                id: Int,
                portalAsker: String,
            ): Unit = ???

            // Replier Task
            def reply(
                r: Any,
                portal: String,
                asker: String,
                key: Key[Long],
                id: Int,
                portalAsker: String,
            ): Unit = ???
          }

          // prepare task
          val preparedTask = TaskExecution.prepareTask(task, tctx)

          // used to execute a wrapped event on the task
          def execute(event: WrappedEvent[T]): Unit =
            event match
              case portals.runtime.WrappedEvents.Event(key, event) =>
                tctx.state.key = key
                tctx.key = key
                tctx.task = preparedTask
                preparedTask.onNext(event)
              case portals.runtime.WrappedEvents.Atom =>
                preparedTask.onAtomComplete
              case portals.runtime.WrappedEvents.Seal =>
                preparedTask.onComplete
              case portals.runtime.WrappedEvents.Error(t) =>
                preparedTask.onError(t)
              case _ => ???

          // event stash and atom stash are used to store away events until alignment is completed,
          // at which point they can be unstashed to execute one atom.

          ////////////////////////////////////////////////////////////////////////
          // Atom Stash
          ////////////////////////////////////////////////////////////////////////
          var _atom_stash = Map.empty[String, List[List[portals.runtime.WrappedEvents.WrappedEvent[T]]]]

          def atom_stash(subscriptionId: String, atom: List[portals.runtime.WrappedEvents.WrappedEvent[T]]) =
            _atom_stash = _atom_stash.updated(subscriptionId, _atom_stash(subscriptionId) :+ atom)

          // unstash one atom for each subscriptionId
          def atom_unstash_one(): Unit =
            _atom_stash.foreach { case (subscriptionId, atoms) =>
              val atom = atoms.head
              // execute the atom on the task
              atom.foreach { x => execute(x) }
            }
            _atom_stash = _atom_stash.view.mapValues(_.tail).toMap

          // get blocking subscriptionIds
          def atom_stash_get_blocking(): Set[String] =
            _atom_stash.filter { case (subscriptionId, atoms) =>
              !atoms.isEmpty
            }.keySet

          ////////////////////////////////////////////////////////////////////////
          // Event Stash
          ////////////////////////////////////////////////////////////////////////
          /** Stash, for stashing a building atom (unfinished atom) */
          var _stash = Map.empty[String, List[portals.runtime.WrappedEvents.WrappedEvent[T]]]

          // add item to building stash
          def stash(subscriptionId: String, item: portals.runtime.WrappedEvents.WrappedEvent[T]): Unit =
            _stash = _stash.updated(subscriptionId, _stash(subscriptionId) :+ item) // is fine because initialized

          // fuse the stashed atom for the subscriptionId
          def stash_fuse(subscriptionId: String): Unit =
            atom_stash(subscriptionId, _stash(subscriptionId)) // is fine because they have been initialized
            _stash = _stash.updated(subscriptionId, Nil)

          def _stash_init(subscriptionId: String): Unit =
            _atom_stash = _atom_stash.updated(subscriptionId, List.empty)
            _stash = _stash.updated(subscriptionId, List.empty)

          // initialize stash and blocking and sealed
          deps.foreach { dep => _stash_init(dep) }
          nonBlocking = deps
          nonSeald = deps

          Behaviors.receiveMessage { case Event(sender, event) =>
            if seald.contains(sender) then Behaviors.same // ignore seald senders
            else
              event match
                // when receiving an event, if blocking then stash, else execute it.
                case portals.runtime.WrappedEvents.Event(_, _) =>
                  if blocking.contains(sender) then stash(sender, event.asInstanceOf)
                  else execute(event.asInstanceOf)
                  Behaviors.same
                // when receiving an atom,
                // turn into blocking, all are blocking, then the atom has been aligned
                // and we can continue by unstashing the latest atom.
                case portals.runtime.WrappedEvents.Atom =>
                  stash_fuse(sender)
                  blocking = blocking + sender
                  nonBlocking = nonBlocking - sender
                  if (nonBlocking.isEmpty) then
                    atom_unstash_one()
                    execute(portals.runtime.WrappedEvents.Atom)
                    subscribers.foreach { _ ! Event(path, portals.runtime.WrappedEvents.Atom) }
                    nonBlocking = blocking
                    blocking = Set.empty
                    // TODO: try out this shouldn't be necessary AFAIK.
                    blocking = atom_stash_get_blocking()
                    nonBlocking = nonBlocking.diff(blocking)
                  Behaviors.same
                case portals.runtime.WrappedEvents.Seal =>
                  seald += sender
                  nonSeald -= sender
                  if nonSeald.isEmpty then
                    subscribers.foreach { _ ! Event(path, portals.runtime.WrappedEvents.Seal) }
                    preparedTask.onComplete
                    Behaviors.stopped
                  else Behaviors.same
                case portals.runtime.WrappedEvents.Error(t) =>
                  subscribers.foreach { _ ! Event(path, portals.runtime.WrappedEvents.Error(t)) }
                  preparedTask.onError(t)
                  throw t
                  Behaviors.stopped
                case _ => ???

          }
        }
