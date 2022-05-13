package pods.workflows

private[pods] sealed trait IChannel[T]:
  private[pods] val worker: Worker[EventWithId[T], EventWithId[T]]
  def close(): Unit

private[pods] class IChannelImpl[T] extends IChannel[T]:
  import Workers.*
  val id = scala.util.Random().nextInt()
  private[pods] val worker = Workers[EventWithId[T], EventWithId[T]]()
    .withOnNext(_worker ?=> t => _worker.submit(EventWithId(id, t.event)))
    .build()
  override def close(): Unit = worker.close()

private[pods] object IChannel:
  def apply[T](): IChannel[T] = new IChannelImpl[T] {}

// @main def workerWithSenderExample(): Unit =
//   import Workers.*
//   type T = String

//   case class Event[T](sender: String, event: T)

//   val worker1 = Workers[T, Event[T]]()
//     .withOnNext(worker ?=>
//       event =>
//         val e = Event("worker1", event)
//         worker.submit(e)
//     )
//     .build()

//   val worker2 = Workers[T, Event[T]]()
//     .withOnNext(worker ?=>
//       event =>
//         val e = Event("worker2", event)
//         worker.submit(e)
//     )
//     .build()

//   val worker3 = Workers[Event[T], T]()
//     .withOnNext(event => println(event))
//     .build()

//   // worker3 subscribes to both worker1 and worker2
//   worker1.subscribe(worker3)
//   worker2.subscribe(worker3)

//   worker1.onNext("hello world")
//   worker1.onNext("hello world")
//   worker1.onNext("hello world")
//   worker1.onNext("hello world")
//   worker1.onNext("hello world")
//   worker2.onNext("hello world")
//   Thread.sleep(100)
