package portals.runtime.local

import scala.concurrent.duration.Duration
import scala.concurrent.Await

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.Scheduler
import akka.util.Timeout

import portals.application.generator.Generator
import portals.application.sequencer.Sequencer
import portals.application.splitter.Splitter
import portals.application.task.GenericTask
import portals.runtime.interpreter.InterpreterEvents.*
import portals.runtime.WrappedEvents.*

object AkkaRunnerBehaviors:
  /** Internal API. Events used by Akka Runner Behaviors. */
  object Events:
    // Publish subscribe event type, used for the Behaviors.
    sealed trait PubSubEvent

    // Request, either an event or a subscribe request.
    sealed trait PubSubRequest extends PubSubEvent
    // Wrapped `event`, sent from `path`.
    case class Event[+T](path: String, event: WrappedEvent[T]) extends PubSubRequest with SplitterCommand
    // `subscriber` wishes to subscribe, add `subscriber` to set of subs.
    case class Subscribe(subscriber: ActorRef[Event[_]], replyTo: ActorRef[PubSubReply]) extends PubSubRequest

    // Reply, successfull subscribe reply.
    sealed trait PubSubReply extends PubSubEvent
    // Subscription was successfull.
    case object SubscribeSuccessfull extends PubSubReply

    // TODO: replace with timers, instead.
    sealed trait GeneratorCommand
    // TODO: replace with timers, instead.
    case object Next extends GeneratorCommand

    sealed trait SplitterCommand
    case class SplitterSubscribe[T](
        subscriberPath: String,
        subscriber: ActorRef[Event[T]],
        filter: T => Boolean,
        replyTo: ActorRef[SplitterReply]
    ) extends SplitterCommand
    sealed trait SplitterReply
    case object SplitterSubscribeSuccessfull extends SplitterReply

/** Internal API. Create Akka Actor Behaviors. */
trait AkkaRunnerBehaviors:
  import AkkaRunnerBehaviors.Events.*

  /** Connect a stream to a sequencer. Blocking. */
  def connect(stream: ActorRef[PubSubRequest], sequencer: ActorRef[Event[_]])(using Timeout, Scheduler) =
    val fut = stream.ask(replyTo => Subscribe(sequencer, replyTo))
    Await.result(fut, Duration.Inf)

  /** Behavior for an atomic stream with name `path` and set of `subscribers`.
    */
  def atomicStream[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[PubSubRequest]

  /** Behavior for a generator with name `path`, inner `generator`, and an
    * output `stream`.
    */
  def generator[T](path: String, generator: Generator[T], stream: ActorRef[Event[T]]): Behavior[GeneratorCommand]

  /** Behavior for a sequencer with name `path`, inner `sequencer`, and an
    * output `stream`.
    */
  def sequencer[T](path: String, sequencer: Sequencer[T], stream: ActorRef[Event[T]]): Behavior[Event[T]]

  /** Behavior for a splitter with name `path`, inner `splitter`. */
  def splitter[T](path: String, splitter: Splitter[T]): Behavior[SplitterCommand]

  /** Behavior for a source in a workflow, with set of subscribers. */
  def source[T](path: String, subscribers: Set[ActorRef[Event[T]]] = Set.empty): Behavior[Event[T]]

  /** Behavior for a sink in a workflow, with a set of subscribers, and a set of
    * dependencies.
    */
  def sink[T](
      path: String,
      subscribers: Set[ActorRef[Event[T]]] = Set.empty,
      deps: Set[String] = Set.empty
  ): Behavior[Event[T]]

  /** Behavior for a task in a workflow, with a set of subscribers, and a set of
    * dependencies.
    */
  def task[T, U](
      path: String,
      task: GenericTask[T, U, Nothing, Nothing],
      subscribers: Set[ActorRef[Event[U]]] = Set.empty,
      deps: Set[String] = Set.empty
  ): Behavior[Event[T]]
