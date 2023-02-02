package portals

import scala.concurrent.duration.Duration
import scala.concurrent.Await

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.Scheduler
import akka.util.Timeout

import portals.*

object AkkaRunner:
  object Events:
    sealed trait PubSubRequest
    case class Event[+T](path: String, event: WrappedEvent[T]) extends PubSubRequest
    case class Subscribe(subscriber: ActorRef[Event[_]], replyTo: ActorRef[PubSubReply]) extends PubSubRequest

    sealed trait PubSubReply
    case object SubscribeSuccessfull extends PubSubReply

    sealed trait GeneratorCommand
    case object Next extends GeneratorCommand

trait AkkaRunner:
  import AkkaRunner.Events.*

  def connect(stream: ActorRef[PubSubRequest], sequencer: ActorRef[Event[_]])(using Timeout, Scheduler) =
    val fut = stream.ask(replyTo => Subscribe(sequencer, replyTo))
    Await.result(fut, Duration.Inf)

  def atomicStream[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[PubSubRequest]

  def generator[T](path: String, generator: Generator[T], stream: ActorRef[Event[T]]): Behavior[GeneratorCommand]

  def sequencer[T](path: String, sequencer: Sequencer[T], stream: ActorRef[Event[T]]): Behavior[Event[T]]

  def source[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[Event[T]]

  def sink[T](
      path: String,
      subscribers: Set[ActorRef[Event[T]]] = Set.empty,
      deps: Set[String] = Set.empty
  ): Behavior[Event[T]]

  def task[T, U](
      path: String,
      task: GenericTask[T, U, Nothing, Nothing],
      subscribers: Set[ActorRef[Event[U]]] = Set.empty,
      deps: Set[String] = Set.empty
  ): Behavior[Event[T]]
