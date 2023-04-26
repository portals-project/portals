package portals.runtime.executor

import scala.collection.immutable.VectorBuilder
import scala.collection.mutable.Map

import portals.application.sequencer.Sequencer
import portals.runtime.local.AkkaRunnerBehaviors
import portals.runtime.WrappedEvents.*

/** Internal API. Executor for Generators. */
private[portals] class SequencerExecutorImpl[T]:
  // setup
  private var path: String = _
  private var sequencer: Sequencer[T] = _
  private val atoms: Map[String, Vector[Vector[WrappedEvent[T]]]] = Map.empty
  private val buffers: Map[String, VectorBuilder[WrappedEvent[T]]] = Map.empty

  /** Setup the `sequencer` for `_path`. */
  def setup(_path: String, _sequencer: Sequencer[T]): Unit =
    this.path = _path
    this.sequencer = _sequencer

  /** Run the sequencer, produces either some Some(Atom) or nothing (None). */
  def run(event: AkkaRunnerBehaviors.Events.Event[T]): Option[Vector[WrappedEvent[T]]] =
    // add event to buffer
    buffers += event.path -> buffers.getOrElse(event.path, VectorBuilder[WrappedEvent[T]]()).addOne(event.event)

    event.event match
      // if the event finishes an atom, then ask the sequencer to make a choice
      case Atom =>
        // discard an empty atom
        if buffers(event.path).result().length == 1 then
          // clear buffer
          buffers(event.path).clear()
          None
        else
          // add atom to atoms
          atoms += event.path -> atoms.getOrElse(event.path, Vector.empty).appended(buffers(event.path).result())
          // clear buffer
          buffers(event.path).clear()

          // get the sequencers choice
          sequencer.sequence(atoms.keys.toList*) match
            case Some(choice) =>
              val atom = atoms(choice).head
              atoms += choice -> atoms(choice).tail
              if atoms(choice).isEmpty then atoms -= choice // remove sender if no more atoms
              Some(atom)
            case None =>
              None
      case _ => None
