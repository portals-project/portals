*** Channels ***

```scala
// Tasks are combined via channels.
// This can be achieved by using the connect factory, that subscribes the main input
// of task1 to the main output of task2.
val channel = Channels.connect(task1, task2)

// One alternative is to also have channel combiners.
// With channel combiners we can merge channels.
// In particular, by providing a strategy we can also describe how the channels
// are combined. 
// With strategies we can describe how atoms can be synchronized for each task. 
// The alternative would be to perform this synchronization at the input of the 
// task.
val merged = Channels.combine(strategy)(channel1, channel2)
```