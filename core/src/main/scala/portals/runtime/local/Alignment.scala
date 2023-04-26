package portals.runtime.local

import scala.collection.mutable.Set

import portals.runtime.local.AkkaRunnerBehaviors.Events.Event
import portals.runtime.WrappedEvents

object Alignment:
  sealed trait AlignedMessage[+T]
  case class BatchMessage[+T](seq: Seq[WrappedEvents.WrappedEvent[T]]) extends AlignedMessage[T]
  case class EventMessage[+T](event: WrappedEvents.WrappedEvent[T]) extends AlignedMessage[T]
  case class ErroredMessage[+T](event: WrappedEvents.WrappedEvent[T]) extends AlignedMessage[T]
  case class SealedMessage[+T](event: WrappedEvents.WrappedEvent[T]) extends AlignedMessage[T]
  case object NoMessage extends AlignedMessage[Nothing]

/** Internal API. Align event streams, returns aligned messages. */
class Alignment[T]:
  import Alignment.*

  // set of blocking and non-blocking incoming streams
  private val blocking = Set.empty[String]
  private val nonBlocking = Set.empty[String]

  // set of sealed and non-sealed incoming streams
  private val seald = Set.empty[String]
  private val nonSeald = Set.empty[String]

  // atom buffers, one per incoming path
  private var atomBuffers = new AtomBuffers[WrappedEvents.WrappedEvent[T]]()

  /** Setup the executor by initializing it for the set of dependencies. */
  def setup(deps: Seq[String]): Unit =
    // initialize stash and blocking and sealed
    nonBlocking.addAll(deps)
    nonSeald.addAll(deps)

  /** Handle a message, aligns it, returns aligned messages or atoms. */
  def receiveMessage(msg: Event[T]): AlignedMessage[T] = msg match
    case Event(sender, event) =>
      if seald.contains(sender) then NoMessage // ignore seald senders
      else
        event match
          //////////////////////////////////////////////////////////////////////
          // Event
          //////////////////////////////////////////////////////////////////////

          // when receiving an event, if blocking then stash, else execute it.
          case WrappedEvents.Event(_, _) =>
            // if blocking then stash
            if blocking.contains(sender) then
              atomBuffers.addOne(sender, event)
              NoMessage
            // else execute the event
            else EventMessage(event)

          //////////////////////////////////////////////////////////////////////
          // Atom Barrier
          //////////////////////////////////////////////////////////////////////

          // when receiving an atom, turn into blocking, until all are blocking, then the atom has been aligned,
          // and we can continue by unstashing the latest atom.
          case WrappedEvents.Atom =>
            // stash if blocking
            if blocking.contains(sender) then
              atomBuffers.addOne(sender, event)
              NoMessage
            else
              // start blocking the sender
              blocking.add(sender)
              nonBlocking.remove(sender)

              // if aligned
              if nonBlocking.isEmpty then
                val r = BatchMessage[T](
                  // emit atom barrier
                  Seq(WrappedEvents.Atom)
                    // emit all events after the atom
                    .appendedAll(
                      atomBuffers.removeHeadWhileAll { case WrappedEvents.Event(_, _) => true; case _ => false }
                    )
                )
                // set as blocking all buffers with an atom at the head
                val tmp = atomBuffers.removeHeadIfAll { case WrappedEvents.Atom => true; case _ => false }.toSet
                nonBlocking.addAll(blocking.diff(tmp))
                blocking.clear()
                blocking.addAll(tmp)
                r
              else NoMessage

          //////////////////////////////////////////////////////////////////////
          // Seal
          //////////////////////////////////////////////////////////////////////

          case WrappedEvents.Seal =>
            seald += sender
            nonSeald -= sender
            if nonSeald.isEmpty then SealedMessage(event)
            else NoMessage

          //////////////////////////////////////////////////////////////////////
          // Error
          //////////////////////////////////////////////////////////////////////

          case WrappedEvents.Error(t) =>
            ErroredMessage(event)

          case _ => ??? // ignore rest for now :)
