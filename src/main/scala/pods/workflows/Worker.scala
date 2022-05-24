package pods.workflows

import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.Flow.Processor
import java.util.concurrent.Flow.Subscriber
import java.util.concurrent.Flow.Publisher
import java.util.concurrent.Flow.Subscription
import java.util.logging.Logger

private[pods] trait Worker[I, O] extends Processor[I, O], Subscriber[I], Publisher[O]:
  def onSubscribe(s: Subscription): Unit
  def onNext(t: I): Unit
  def onError(t: Throwable): Unit
  def onComplete(): Unit
  def subscribe(s: Subscriber[_ >: O]): Unit
  def submit(event: O): Int
  def close(): Unit

private[pods] trait WorkerImpl[I, O] extends SubmissionPublisher[O] with Worker[I, O]

private[pods] case class WorkerBuilder[I, O](
    _onSubscribe: Worker[I, O] => Subscription => Unit = (_: Worker[I, O]) => _ => ???,
    _onNext: Worker[I, O] => I => Unit = (_: Worker[I, O]) => I => ???,
    _onError: Worker[I, O] => Throwable => Unit = (_: Worker[I, O]) => _ => ???,
    _onComplete: Worker[I, O] => () => Unit = (_: Worker[I, O]) => () => ???
)

object Workers:
  def apply[I, O](): WorkerBuilder[I, O] =
    new WorkerBuilder().withOnSubscribe((s: Subscription) => s.request(Long.MaxValue))

  extension [I, O](worker: WorkerBuilder[I, O]) {
    def withOnSubscribe(
        f: Worker[I, O] ?=> Subscription => Unit
    ): WorkerBuilder[I, O] =
      worker.copy(_onSubscribe = w => f(using w))

    def withOnNext(f: Worker[I, O] ?=> I => Unit): WorkerBuilder[I, O] =
      worker.copy(_onNext = w => f(using w))

    def withOnError(
        f: Worker[I, O] ?=> Throwable => Unit
    ): WorkerBuilder[I, O] =
      worker.copy(_onError = w => f(using w))

    def withOnComplete(
        f: Worker[I, O] ?=> () => Unit
    ): WorkerBuilder[I, O] =
      worker.copy(_onComplete = w => f(using w))

    def build(): Worker[I, O] =
      new WorkerImpl[I, O] {
        self =>
        override def onSubscribe(s: Subscription): Unit = worker._onSubscribe(self)(s)
        override def onNext(t: I): Unit = worker._onNext(self)(t)
        override def onError(t: Throwable): Unit = worker._onError(self)(t)
        override def onComplete(): Unit = worker._onComplete(self)
      }
  }

// @main def chainedWorkerExample(): Unit =
//   import Workers.*
//   type T = String
//   val worker1 = Workers[T, T]()
//     .withOnNext(worker ?=> event => worker.submit(event))
//     .build()
//   val worker2 = Workers[T, T]()
//     .withOnNext(worker ?=> event => worker.submit(event))
//     .build()
//   val worker3 = Workers[T, T]()
//     .withOnNext(event => println(event))
//     .build()
//   worker1.subscribe(worker2)
//   worker2.subscribe(worker3)
//   worker1.submit("hello world")
//   Thread.sleep(100)
