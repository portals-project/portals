# Streams
```scala
// Some event type
type Event

// A stream can be created by calling a factory method in the Streams object
val stream: Stream[Event] = Streams.stream[Event]()

// Streams have an input reference IRef and an output reference ORef
// Alternative: this coulld be called IStream, OStream instead.
val iref: StreamIRef[Event] = stream.iref
val oref: StreamORef[Event] = stream.oref

// We can send events to streams by submitting them to the iref
iref.submit(event)
// Lib: we also support the bang syntax
iref ! event

// Streams are sharded per-key, all events on the stream are associated with some key. 
// The key is typically inferred from the implicit context.key
iref.submit(event)(using context)
// Lib:: it can also be specified explicitly, e.g. when submitting events to a stream.
iref.submit(event, key)

// Streams can be connected to other streams.
// This can be done by calling the connect method on the Streams object, this 
// creates a subscription between the oref and iref.
// Alternetive: this might be a Subscription instead of Connection.
val connection: Connection = Streams.connect(oref, iref)
// A connection can be cancelled (this cancels the subscription)
// note: this is not possible with reactive streams, the subscription is only known to the receiver
connection.cancel()

// Lib: We can create streams from existing streams.
// One such way is to combine two or more streams into a single stream.
val ostream = Streams.combine[](List(istream1, istream2, ...))
// Further, we could specify the way in which streams (and atoms) are combined
// through providing a combiner strategy.
// An example would be to combine one atom from each incoming stream into a new atom.
val ostream = Streams.combine(strategy)(List(istream1, istream2, ...))

// We can seal a stream, which signals that no more events will be submitted
istream.seal()
// Alternative:
istream.close()

// We can then create a tick on the stream.
// The tick creates an atom of events, that consists of all events that were
// submitted before the tick to the stream, and after the previous tick to the 
// stream.
istream.tick()
// Alternative: this can also be called fuse, to stick with the whole "atom" thing
istream.fuse() 

// A stream should be transactional, that is we should be able to perform a 
// 2-phase-commit to ensure that the data is persisted.

// Precommit, returns commit id which can be used to track if commit was executed,
// this is needed in case the client fails, and wants to know later on if the
// commit was executed or not.
val commit_id = istream.precommit()
// check if the commit was executed
istream.isCommitted(commit_id)

// block until tick is atomically committed
istream.commit()
// or commit async
istream.commitAsync()

// if something went wrong we can also rollback (before the commit has completed)
istream.rollback()

// A stream should be able to replay events starting from some sequence number.
subscription.replayFrom(sequence_number)
```