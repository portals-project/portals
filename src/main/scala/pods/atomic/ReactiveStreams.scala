package pods.atomic

/** Reactive Streams interface */
trait Processor[T, S] extends java.util.concurrent.Flow.Processor[T, S]
trait Publisher[T] extends java.util.concurrent.Flow.Publisher[T]
trait Subscriber[T] extends java.util.concurrent.Flow.Subscriber[T]
trait Subscription extends java.util.concurrent.Flow.Subscription