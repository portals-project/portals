package portals.libraries.actor

import scala.annotation.experimental
import scala.collection.mutable.Queue
import scala.util.Try

import org.junit.Assert._

import portals.libraries.actor.ActorBehaviors

@experimental
object ActorTestUtils:
  class TestBehavior[T](wrappedBehavior: ActorBehavior[T]):
    // queue of received messages of the wrapped actor behavior
    private val queue: Queue[T] = Queue[T]()

    // enqueue a message to the receive queue
    private def enqueue(msg: T): Unit =
      queue.enqueue(msg)

    // the behavior which wraps around the submitted behavior, it intercepts all messages
    val behavior = ActorBehaviors.InitBehavior[T] { ctx =>
      var innerBehavior = ActorBehaviors.prepareBehavior(wrappedBehavior, ctx)
      ActorBehaviors.ReceiveActorBehavior[T] { ctx => msg =>
        enqueue(msg)
        innerBehavior match
          case ActorBehaviors.ReceiveActorBehavior(f) =>
            f(ctx)(msg) match
              case newInnerBehavior @ ActorBehaviors.ReceiveActorBehavior(f) =>
                innerBehavior = newInnerBehavior
                ActorBehaviors.same
              case ActorBehaviors.StoppedBehavior =>
                ActorBehaviors.stopped
              case ActorBehaviors.SameBehavior =>
                ActorBehaviors.same
              case _ => ???
          case _ => ???
      }
    }

    // take the first element from the receive queue and dequeue it
    def receive(): Option[T] =
      Try(queue.dequeue()).toOption

    // receive all messages from the queue, does not dequeue the elements
    def receiveAll(): Seq[T] =
      queue.toSeq

    // check if the receive queue is empty
    def isEmpty(): Boolean =
      queue.isEmpty

    // true if the receive queue contains the given element
    def contains(element: T): Boolean =
      queue.contains(element)

    // empty the receive queue
    def clear(): Unit =
      queue.clear()

    // receive and assert that the received element is the same as the given element
    def receiveAssert(event: T): this.type = { assertEquals(Some(event), receive()); this }

    // assert that the receive queue is empty
    def isEmptyAssert(): this.type = { assertEquals(true, isEmpty()); this }
