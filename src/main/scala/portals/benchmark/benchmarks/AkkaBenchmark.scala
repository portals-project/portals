package portals.benchmark.benchmarks

import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior

import com.typesafe.config.ConfigFactory

import portals.benchmark.*
import portals.benchmark.BenchmarkUtils.*

object AkkaBenchmark extends Benchmark:
  private val config = BenchmarkConfig()
    .setRequired("--nEvents") // 1024 * 1024
    .setRequired("--nActors") // 1024 * 1024
    .setRequired("--sWorkload") // "pingPong"

  private def terminate(system: ActorSystem[_]): Unit =
    system.terminate()
    // Await.result({ system.terminate(); system.whenTerminated }, 10.seconds)

  private case class Start()

  private object ForkJoinThroughput:
    sealed trait Command
    case object Next extends Command

    def countingBehavior(completer: CountingCompletionWatcher, nEvents: Int): Behavior[Command] =
      Behaviors.setup { ctx =>
        var counter = 0
        Behaviors.receiveMessage { case Next =>
          counter += 1
          if counter == nEvents then
            completer.complete()
            Behaviors.stopped
          else Behaviors.same
        }
      }

    def runner(completer: CountingCompletionWatcher, nEvents: Int, nActors: Int): Behavior[Start] =
      Behaviors.setup { ctx =>
        val actors = (0 until nActors).map { _ =>
          ctx.spawnAnonymous(countingBehavior(completer, nEvents))
        }
        Behaviors.receiveMessage { case Start() =>
          (0 until nEvents).foreach { i =>
            actors.foreach { actor =>
              actor ! Next
            }
          }
          Behaviors.same
        }
      }

  private object CountingActor:
    sealed trait Command
    case object Increment extends Command

    private def countingBehavior(completer: CompletionWatcher, nEvents: Int): Behavior[Command] =
      Behaviors.setup { ctx =>
        var counter = 0
        Behaviors.receiveMessage { case Increment =>
          counter += 1
          if counter == nEvents then completer.complete()
          Behaviors.same
        }
      }

    def runner(completer: CompletionWatcher, nEvents: Int): Behavior[Start] =
      Behaviors.setup { ctx =>
        val countingActor = ctx.spawnAnonymous(countingBehavior(completer, nEvents))
        Behaviors.receiveMessage { case Start() =>
          for _ <- 0 until nEvents do countingActor ! Increment
          Behaviors.same
        }
      }

  private object ThreadRing:
    sealed trait Command
    case class NextActor(x: Int, nextActor: ActorRef[Command]) extends Command
    case class Next(x: Int) extends Command

    private def threadRingBehavior(
        nextActor: Option[ActorRef[Command]],
        completer: CompletionWatcher
    ): Behavior[Command] =
      Behaviors.receive { (ctx, msg) =>
        msg match
          case NextActor(x, _nextActor) =>
            if x == 0 then completer.complete()
            else _nextActor ! Next(x - 1)
            threadRingBehavior(Some(_nextActor), completer)
          case Next(x) =>
            if x == 0 then
              nextActor.get ! Next(x - 1)
              completer.complete()
              Behaviors.stopped
            if x < 0 then
              nextActor.get ! Next(x - 1)
              Behaviors.stopped
            else
              nextActor.get ! Next(x - 1)
              Behaviors.same
      }

    def runner(completer: CompletionWatcher, nEvents: Int, nActors: Int): Behavior[Start] =
      Behaviors.setup { ctx =>
        val firstActor = ctx.spawnAnonymous(threadRingBehavior(None, completer))
        var prev = firstActor
        val actors = (1 until nActors).map { _ =>
          prev = ctx.spawnAnonymous(threadRingBehavior(Some(prev), completer))
        }
        firstActor ! NextActor(nEvents, prev)
        Behaviors.same
      }

  private object PingPong:
    case class Ping(x: Int, replyTo: ActorRef[Ping])

    private def pingerponger(completer: CompletionWatcher): Behavior[Ping] =
      Behaviors.receive { (ctx, x) =>
        x match
          case Ping(x, replyTo) =>
            if (x == 0) then completer.complete()
            else replyTo ! Ping(x - 1, ctx.self)
            Behaviors.same
      }

    def runner(completer: CompletionWatcher, nEvents: Int): Behavior[Start] = Behaviors.receive { (ctx, start) =>
      ctx.log.error("Starting")
      val pinger = ctx.spawnAnonymous(pingerponger(completer))
      val ponger = ctx.spawnAnonymous(pingerponger(completer))
      pinger ! Ping(nEvents, ponger)
      Behaviors.same
    }

  override val name = "AkkaBenchmark"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val nActors = config.getInt("--nActors")
    val sWorkload = config.get("--sWorkload")

    // see https://doc.akka.io/docs/akka/current/coordinated-shutdown.html
    val cf = ConfigFactory
      .parseString(
        s"""
        akka.coordinated-shutdown.terminate-actor-system = off
        akka.coordinated-shutdown.run-by-actor-system-terminate = off
        akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
        akka.cluster.run-coordinated-shutdown-when-down = off
        """
      )
      .withFallback(ConfigFactory.defaultApplication)

    val completer = CompletionWatcher()
    val countingCompleter = CountingCompletionWatcher(nActors)

    val runnerBehavior = sWorkload match
      case "pingPong" => PingPong.runner(completer, nEvents)
      case "threadRing" => ThreadRing.runner(completer, nEvents, nActors)
      case "countingActor" => CountingActor.runner(completer, nEvents)
      case "forkJoinThroughput" => ForkJoinThroughput.runner(countingCompleter, nEvents / nActors, nActors)
      case _ => throw new IllegalArgumentException(s"Unknown workload: $sWorkload")

    val runner = ActorSystem[Start](runnerBehavior, sWorkload, cf)

    runner ! Start()

    // wait for completion
    try
      if sWorkload == "forkJoinThroughput" then countingCompleter.waitForCompletion()
      else completer.waitForCompletion()
    catch case e => { terminate(runner); throw e }

    terminate(runner)
