```scala
// **** Streams ****
// This is an overview of the streams API.

// Consider some event type
type Event

// A stream can be created by calling a factory method in the Streams object
val stream: Stream[Event] = Streams.stream[Event]()

// Streams have an input reference IRef and an output reference ORef
val iref: StreamIRef[Event] = stream.iref
val oref: StreamORef[Event] = stream.oref

// Note: the IRef and ORef extend the reactive streams Publisher and Subscriber interfaces
// (or at least is very similar).
// The StreamIRef implementation simply forwards any of the events it receives to the ORef.
trait StreamIRef extends java.util.concurrent.Flow.Subscriber:
  ??? 
trait StreamORef extends java.util.concurrent.Flow.Publisher:
  ??? 
trait java.util.concurrent.Flow.Subscriber:
  def onSubscribe(s: Subscription): Unit
  def onNext(t: I): Unit
  def onError(t: Throwable): Unit
  def onComplete(): Unit
trait java.util.concurrent.Flow.Publisher:
  def subscribe(s: Subscriber[_ >: O]): Unit

// We can send events to streams by submitting them to the iref
iref.submit(event)
// Lib: we also support the bang syntax
iref ! event

// Streams are sharded per-key, all events on the stream are associated with some
// key. 
// The key is typically inferred from the context.key
iref.submit(event)(using context)
// Lib:: it can also be specified explicitly, e.g. when submitting events to a stream.
iref.submit(event, key)
// Lib:: when nothing is supplied we could default to some strategy
iref.submit(event)

// Streams can be connected to other streams.
// This can be done by calling the connect method on the Streams object.
// A connection is simply a stream that connects the oref of one stream to the 
// iref of another, which forms a subscription between the oref and iref.
// Once a connection has been established, events that are published by the oref
// will be received by the iref.
// Note: Only complete atoms will be received, the iref will not receive quarks 
// or other half-atoms.
val connection: Connection = Streams.connect(oref, iref)
// A connection can be cancelled (this cancels the subscription)
connection.cancel()

// Lib: We can create streams from existing streams.
// One such way is to combine two or more streams into a single stream.
// Note: this is not necessarily part of the core module, this could be implemented
// as part of a library instead using workflows and streams.
val ostream = Streams.combine[](List(istream1, istream2, ...))
// Lib: combiner strategies.
// We can specify the way in which streams (and the atoms) can be combined 
// through a combiner strategy.
// For example, we could specify that the resulting stream should consist of
// molecules with one atom from each stream, i.e. one atom from each stream is 
// taken to form a new larger atom.
// We can also consider other arbitrary strategies.
val ostream = Streams.combine(strategy)(List(istream1, istream2, ...))

// We can seal a stream, which signals that no more events will be submitted
istream.seal()
// Alternative:
istream.close()

// **** Tick ****
// We can then create a tick on the stream.
// The tick creates an atom of events, that consists of all events that were
// submitted before the tick to the stream, and after the previous tick to the 
// stream.
istream.tick()
// Alternative: this can also be called fuse, to stick with the whole "atom" thing
istream.fuse() 

// *** Commit ***
// A stream should be transactional, that is we should be able to perform a 
// 2-phase-commit to ensure that the data is persisted.

// Precommit, returns commit id which can be used to track if commit was executed,
// this is needed in case the client fails, and wants to know later on if the
// commit was executed or not.
val commit_id = istream.precommit()

// block until tick is atomically committed
istream.commit()
// or commit async
istream.commitAsync()

// check if the commit was executed
istream.isCommitted(commit_id)

// if something went wrong we can also rollback (before the commit has completed)
istream.rollback()

// *** Replay ***
// A stream should be able to events starting from some sequence number.
// This can be used to recover from failures.
// This is achieved by calling the replayFrom method on the subscription.
subscription.replayFrom(sequence_number)
```
