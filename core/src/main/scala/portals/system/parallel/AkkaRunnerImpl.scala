package portals.system.parallel

import scala.collection.immutable.VectorBuilder

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior

import portals.*

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
      task: Task[T, U],
      subscribers: Set[ActorRef[Event[U]]] = Set.empty,
      deps: Set[String] = Set.empty
  ): Behavior[Event[T]] =
    AtomicWorkflowExecutor.Task(path, task, subscribers, deps)

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

        val vectorBuilder = VectorBuilder[portals.WrappedEvent[T]]()
        var atom = false
        var error = false
        var seal = false
        def go = !atom && !error && !seal

        ctx.self ! Next

        Behaviors.receiveMessage { case Next =>
          while go && generator.hasNext() do
            generator.generate() match
              case Generator.Event(key, event) =>
                vectorBuilder += portals.Event(key, event)
              case Generator.Atom =>
                vectorBuilder += portals.Atom
                atom = true
              case Generator.Seal =>
                vectorBuilder.clear()
                vectorBuilder += portals.Seal
                seal = true
              case Generator.Error(t) =>
                vectorBuilder.clear()
                vectorBuilder += portals.Error(t)
                error = true

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
        Task[T, T](path, Tasks.identity, subscribers, deps)

    // Task
    object Task:
      def apply[T, U](
          path: String,
          task: Task[T, U],
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
          given tctx: TaskContextImpl[T, U] = TaskContext[T, U]()
          tctx.cb = new TaskCallback[T, U] {
            def submit(key: Key[Int], event: U): Unit =
              subscribers.foreach { sub => sub ! Event(path, portals.Event(tctx.key, event)) }
            def fuse(): Unit = () // do nothing, for now, but deprecated, remove it.
            // ??? // deprecated
            // TODO: this should be removed :/ as no longer is user-space command
          }

          // prepare task
          val preparedTask = Tasks.prepareTask(task, tctx)

          // used to execute a wrapped event on the task
          def execute(event: WrappedEvent[T]): Unit =
            event match
              case portals.Event(key, event) =>
                tctx.state.key = key
                tctx.key = key
                preparedTask.onNext(event)
              case portals.Atom =>
                preparedTask.onAtomComplete
              case portals.Seal =>
                preparedTask.onComplete
              case portals.Error(t) =>
                preparedTask.onError(t)

          // event stash and atom stash are used to store away events until alignment is completed,
          // at which point they can be unstashed to execute one atom.

          ////////////////////////////////////////////////////////////////////////
          // Atom Stash
          ////////////////////////////////////////////////////////////////////////
          var _atom_stash = Map.empty[String, List[List[portals.WrappedEvent[T]]]]

          def atom_stash(subscriptionId: String, atom: List[portals.WrappedEvent[T]]) =
            _atom_stash = _atom_stash.updated(subscriptionId, _atom_stash(subscriptionId) :+ atom)

          // unstash one atom for each subscriptionId
          def atom_unstash_one(): Unit =
            _atom_stash.foreach { case (subscriptionId, atoms) =>
              val atom = atoms.head
              // execute the atom on the task
              atom.foreach { x => execute(x) }
            }
            _atom_stash = _atom_stash.mapValues(_.tail).toMap

          // get blocking subscriptionIds
          def atom_stash_get_blocking(): Set[String] =
            _atom_stash.filter { case (subscriptionId, atoms) =>
              !atoms.isEmpty
            }.keySet

          ////////////////////////////////////////////////////////////////////////
          // Event Stash
          ////////////////////////////////////////////////////////////////////////
          /** Stash, for stashing a building atom (unfinished atom) */
          var _stash = Map.empty[String, List[portals.WrappedEvent[T]]]

          // add item to building stash
          def stash(subscriptionId: String, item: portals.WrappedEvent[T]): Unit =
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
                case portals.Event(_, _) =>
                  if blocking.contains(sender) then stash(sender, event.asInstanceOf)
                  else execute(event.asInstanceOf)
                  Behaviors.same
                // when receiving an atom,
                // turn into blocking, all are blocking, then the atom has been aligned
                // and we can continue by unstashing the latest atom.
                case portals.Atom =>
                  stash_fuse(sender)
                  blocking = blocking + sender
                  nonBlocking = nonBlocking - sender
                  if (nonBlocking.isEmpty) then
                    atom_unstash_one()
                    execute(portals.Atom)
                    subscribers.foreach { _ ! Event(path, portals.Atom) }
                    nonBlocking = blocking
                    blocking = Set.empty
                    // TODO: try out this shouldn't be necessary AFAIK.
                    blocking = atom_stash_get_blocking()
                    nonBlocking = nonBlocking.diff(blocking)
                  Behaviors.same
                case portals.Seal =>
                  seald += sender
                  nonSeald -= sender
                  if nonSeald.isEmpty then
                    subscribers.foreach { _ ! Event(path, portals.Seal) }
                    preparedTask.onComplete
                    Behaviors.stopped
                  else Behaviors.same
                case portals.Error(t) =>
                  subscribers.foreach { _ ! Event(path, portals.Error(t)) }
                  preparedTask.onError(t)
                  throw t
                  Behaviors.stopped

          }
        }
