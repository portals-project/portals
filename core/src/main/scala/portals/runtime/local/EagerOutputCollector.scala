package portals.runtime.local

import akka.actor.typed.ActorRef

import portals.application.task.OutputCollector
import portals.runtime.local.AkkaRunnerBehaviors.Events.Event
import portals.util.Common.Types.Path

private[portals] class EagerOutputCollector extends OutputCollector[Any, Any, Any, Any]:
  import portals.runtime.WrappedEvents.WrappedEvent

  private var subs: Set[ActorRef[Event[_]]] = _
  private var path: Path = _

  def setup(path: Path, subs: Set[ActorRef[Event[_]]]): Unit =
    this.path = path
    this.subs = subs

  def submit(event: WrappedEvent[Any]): Unit =
    subs.foreach { sub => sub ! Event(path, event) }
