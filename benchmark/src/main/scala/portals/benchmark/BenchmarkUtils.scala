package portals.benchmark

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeoutException

import scala.concurrent.duration.*
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.util.hashing.MurmurHash3
import scala.util.Random

import portals.*
import portals.api.builder.*
import portals.api.dsl.DSL.*

object BenchmarkUtils:
  class CompletionWatcher():
    private val atMost = 20.seconds
    private val promise = Promise[Boolean]()
    private val future = promise.future

    def complete(value: Boolean = true): Unit =
      if !promise.isCompleted then promise.success(value)

    def waitForCompletion(): Boolean =
      Await.result(future, atMost)

    def task[T](p: T => Boolean) = TaskBuilder.map[T, T] { x => { if p(x) then complete(true); x } }

    def workflow[T](stream: AtomicStreamRef[T], builder: ApplicationBuilder)(p: T => Boolean): Workflow[T, T] =
      builder
        .workflows[T, T]("completer")
        .source[T](stream)
        .task(task(p(_)))
        .sink[T]()
        .freeze()

  class CountingCompletionWatcher(count: Int):
    private val atMost = 20.seconds
    private val countDownLatch = CountDownLatch(count)

    def complete(): Unit =
      countDownLatch.countDown()

    def waitForCompletion(): Unit =
      if !countDownLatch.await(atMost.length, atMost.unit) then throw TimeoutException()

    def task[T](p: T => Boolean) = TaskBuilder.map[T, T] { x => { if p(x) then complete(); x } }

    def workflow[T](stream: AtomicStreamRef[T], builder: ApplicationBuilder)(p: T => Boolean): Workflow[T, T] =
      builder
        .workflows[T, T]("completer")
        .source[T](stream)
        .task(task(p(_)))
        .sink[T]()
        .freeze()

  object Computation:
    lazy val _r = new Random(System.nanoTime());
    lazy val _hole = Blackhole()
    def apply(difficulty: Int): Unit =
      var _x = _r.nextString(8)
      for i <- 0 until difficulty do _x = MurmurHash3.stringHash(_x.toString()).toString()
      _hole.consume(_x)

  // see https://github.com/msteindorfer/jmh/blob/master/jmh-core/src/main/java/org/openjdk/jmh/infra/Blackhole.java#L291
  class Blackhole():
    val _r = new Random(System.nanoTime());
    val _tlr = _r.nextInt();
    var _mask: Int = 0
    var _hole: Any = null

    def consume[T](t: T): Unit =
      val mask = _mask
      val tlr = _tlr * 1664525 + 1013904223
      if ((tlr & mask) == 0) then
        _hole = t
        _mask = (mask << 1) + 1
