package pods.workflows

import java.util.concurrent.Flow.{Publisher => FPublisher}
import java.util.concurrent.Flow.{Subscriber => FSubscriber}

/** Atomic Publisher */
trait Publisher[T] extends FPublisher[T]:
  override def subscribe(s: FSubscriber[_ >: T]): Unit
