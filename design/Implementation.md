```scala
trait Processor extends AtomicSubscriber with AtomicProcessor

trait StreamIRef extends java.util.concurrent.Flow.Publisher with AtomicPublisher with AtomicSubmitter with Submitter

trait StreamORef extends java.util.concurrent.Flow.Subscriber with AtomicSubscriber 

trait Submitter:
  def seal(): Unit // alternative: close() 
  def submit(event: T): Unit // submit event

trait AtomicSubmitter:
  def fuse(): Unit // or tick()
  def precommit(): Long // returns commit id
  def isCommitted(commit_id: Long): Boolean // check if commit was executed
  def commit(): Unit // block until commit is completed
  def commitAsync(): Unit // commit async
  def rollback(): Unit // rollback

trait AtomicSubscriber:
  def onAtomComplete(id: Long): Unit // or onFuse() or onTick()
  def onCommitComplete(id: Long): Unit

trait AtomicPublisher

trait AtomicSubscription:
  // replay events from a sequence number
  def replayFrom(sequence_number: Long): Unit

trait java.util.concurrent.Flow.Subscriber:
  def onSubscribe(s: Subscription): Unit
  def onNext(t: I): Unit
  def onError(t: Throwable): Unit
  def onComplete(): Unit

trait java.util.concurrent.Flow.Publisher:
  def subscribe(s: Subscriber[_ >: O]): Unit

trait java.util.concurrent.Flow.Subscription:
  def cancel(): Unit
  def request(n: Long): Unit

```