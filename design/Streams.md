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
// From the DSL we also support the bang syntax
iref ! event

// Streams are sharded per-key, all events on the stream are associated with some key. 
// The key is typically inferred from the implicit context.key
iref.submit(event)(using context)
// It can also be specified explicitly, e.g. when submitting events to a stream.
iref.submit(event, key)

// Streams can be connected to other streams.
// To do this we subscribe the IRef to another ORref
val connection = oref.subscribe(iref)
// It can also be done by calling the connect method on the Streams object, this 
// creates a connection between the oref and iref.
val connection = Streams.connect(oref, iref)

// Additionaly, via the library we can create streams from existing streams.
// One such way is to combine two or more streams into a single stream.
val ostream = Streams.combine[](List(ostream1, ostream2, ...))
// Further, we can specify the way in which streams (and atoms) are combined
// through providing a combiner strategy.
// An example would be to combine one atom from each incoming stream into a new atom.
val ostream = Streams.combine(strategy)(List(ostream1, ostream2, ...))

// We can seal a stream, which signals that no more events will be submitted
istream.seal() // or, istream.close()

// We can then create a tick on the stream.
// The tick creates an atom of events, that consists of all events that were
// submitted before the atom to the stream, and after the previous atom to the 
// stream.
istream.fuse() // or, istream.tick()
```