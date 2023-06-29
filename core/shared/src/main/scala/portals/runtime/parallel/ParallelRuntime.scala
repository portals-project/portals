package portals.runtime.parallel

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import scala.util.Random

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.util.Timeout

import com.typesafe.config.ConfigFactory

import portals.application.Application
import portals.application.AtomicStream
import portals.runtime.interpreter.*
import portals.runtime.interpreter.Interpreter
import portals.runtime.parallel.ParallelRuntimePartition.Events.*
import portals.runtime.BatchedEvents.*
import portals.runtime.PortalsRuntime
import portals.runtime.WrappedEvents.*

object ParallelRuntime:
  class SynchronizedState:
    private val state = scala.collection.mutable.Map.empty[Any, Any]
    private val lock = new Object

    def get[K, V](key: K): Option[V] = lock.synchronized:
      state.get(key).asInstanceOf[Option[V]]

    def put[K, V](key: K, value: V): Unit = lock.synchronized:
      state.put(key, value)

    def remove[K, V](key: K): Option[V] = lock.synchronized:
      state.remove(key).asInstanceOf[Option[V]]

  final val STATE = new SynchronizedState()

  class Cache[K, V]:
    private val cache = scala.collection.mutable.Map.empty[K, V]

    def get(key: K): Option[V] =
      val res = cache.get(key)
      if res.isEmpty then
        val other = STATE.get[K, V](key)
        cache.update(key, other.get)
        other
      else res

private[portals] class ParallelRuntime(nThreads: Int) extends PortalsRuntime:
  import ParallelRuntime.*

  private val parallelism: Option[Int] = None

  // see https://doc.akka.io/docs/akka/current/coordinated-shutdown.html
  private val defaultConfig = ConfigFactory
    .parseString(
      s"""
      akka.log-dead-letters-during-shutdown = off
      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-actor-system-terminate = off
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      akka.cluster.run-coordinated-shutdown-when-down = off
      """
    )

  private val cf =
    if parallelism.isDefined then
      ConfigFactory
        .parseString(
          s"""
          akka.actor.default-dispatcher.fork-join-executor.parallelism-min = ${parallelism.get}
          akka.actor.default-dispatcher.fork-join-executor.parallelism-max = ${parallelism.get}
          akka.actor.default-dispatcher.fork-join-executor.parallelism-factor = 1.0
          """
        )
        .withFallback(defaultConfig)
    else defaultConfig

  private val system: akka.actor.ActorSystem = akka.actor.ActorSystem("Portals", cf)

  // FOR BLOCKING AKKA
  given timeout: Timeout = Timeout(3.seconds)
  given scheduler: akka.actor.typed.Scheduler = system.toTyped.scheduler

  // SETUP RUNTIMES
  private val runtimes: Map[Int, ActorRef[Command]] =
    (0 until nThreads).map { i =>
      val ref = system.spawn(
        Behaviors.setup[Message] { ctx =>
          new ParallelRuntimePartition(i, nThreads, ctx)
        },
        s"partition-$i"
      )
      i -> ref
    }.toMap

  runtimes.foreach { case (i, runtime) =>
    STATE.put(i, runtime)
  }

  /* send a synchronous message to a runtime actor */
  def synchMsg[Req, Res](msg: ActorRef[Res] => Req, runtime: ActorRef[Req]): Unit =
    val fut = runtime.ask(replyTo => msg(replyTo))
    Await.result(fut, Duration.Inf)

  /* broadcast a synchronous message to a sequence of runtime actors */
  def synchBroadcast[Req, Res](msg: ActorRef[Res] => Req, runtimes: Seq[ActorRef[Req]]): Unit =
    runtimes.foreach { r => synchMsg(msg, r) }

  /* broadcast a message to a sequence of runtime actors */
  def broadcast[Req](msg: Req, runtimes: Seq[ActorRef[Req]]): Unit =
    runtimes.foreach { r => r ! msg }

  override def launch(application: portals.application.Application): Unit =
    // stop all runtimes
    synchBroadcast(Stop(_), runtimes.values.toSeq)

    // synchronously launch the applications
    runtimes.foreach((i, runtime) =>
      val app = if i == 0 then application else application.copy(generators = List.empty)
      val fut = runtime.ask(replyTo => Launch(app, replyTo))
      Await.result(fut, Duration.Inf)
    )

    // then resume the runtimes
    synchBroadcast(Start(_), runtimes.values.toSeq)

  override def shutdown(): Unit =
    Await.result(system.terminate(), Duration.Inf)
