package portals.benchmark

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeoutException

import scala.concurrent.duration.*
import scala.concurrent.Await
import scala.concurrent.Promise

import portals.*
import portals.DSL.*

object BenchmarkUtils:
  class CompletionWatcher():
    private val atMost = 10.seconds
    private val promise = Promise[Boolean]()
    private val future = promise.future

    def complete(value: Boolean = true): Unit =
      if !promise.isCompleted then promise.success(value)

    def waitForCompletion(): Boolean =
      Await.result(future, atMost)

    def task[T](p: T => Boolean) = Tasks.map[T, T] { x => { if p(x) then complete(true); x } }

    def workflow[T](stream: AtomicStreamRef[T], builder: ApplicationBuilder)(p: T => Boolean): Workflow[T, T] =
      builder
        .workflows[T, T]("completer")
        .source[T](stream)
        .task(task(p(_)))
        .sink[T]()
        .freeze()

  class CountingCompletionWatcher(count: Int):
    private val atMost = 10.seconds
    private val countDownLatch = CountDownLatch(count)

    def complete(): Unit =
      countDownLatch.countDown()

    def waitForCompletion(): Unit =
      if !countDownLatch.await(atMost.length, atMost.unit) then throw TimeoutException()

    def task[T](p: T => Boolean) = Tasks.map[T, T] { x => { if p(x) then complete(); x } }

    def workflow[T](stream: AtomicStreamRef[T], builder: ApplicationBuilder)(p: T => Boolean): Workflow[T, T] =
      builder
        .workflows[T, T]("completer")
        .source[T](stream)
        .task(task(p(_)))
        .sink[T]()
        .freeze()
