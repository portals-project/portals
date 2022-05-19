```scala
// *** Implementation ***

// Everything is implemented on top of "atomic" reactive streams processors, and 
// reactive stream processors (non-atmomic). 
// These are reactive streams that further implement the "atomic" interface.
// The atomic interface...
trait Atomic:
  ???

// Implementation idea:
// TODO: give idea of how things are implemented using the atomic reactive streams processors.
// Streams are implemented as atomic reactive streams processors.
// Workflows and channels are implemented as (non-atomic) reactive streams processors.
```