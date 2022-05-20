# Serializable Updates

```scala
// manually executing a ser update

implicit system = ...
// ##################### how to define ser update in the workflow

// option 1: Explicitly define SER_OP command as an input
// and manually define the ser update for every task
val wf = Workflows.builder().withName("workflow")
  .source[String|SER_OP].withName("text")
  .map(word => (word, 1))
  .withWrapper { ctx ?=> event => wrapped => // wrap the wrapped (map behavior) to catch ser updates
    event match
      case SER_OP => 
        ... // handle ser updates
        ctx.emit(SER_OP) // here we need to emit SER_OP to the next task
      case _ => wrapped(event) // wrapped handles event
  } 
  .sink[(String, Int)|SER_OP]
  .build()
  .launch()

// option 2: Extend workflow to handle SER_OPs uniformly on all tasks
val wf = ... // some workflow
  // this could be implemented with an allWithWrapper method if we also handle the wrapped event and emit SER_OP
  .allWithSerUpdate { ctx ?=> ser_op => // handle ser updates on all tasks uniformly
    ser_op match
      case SER_OP(...) => 
        ctx.state... // for example, do something with state
        ... // handle ser updates
        // we don't need to emit Ser_op as this is automatically done by allWithSerUpdate
  }
  .build()

// option 3: save ser_op in the state and execute it later at time of atom-barrier
val wf = ... // some workflow
  .allWithWrapper(ctx ?=> event => wrapped =>
    event match
      case SER_OP(...) => 
        ctx.state.set("ser_op", event) // save ser_op in the state
      case _ => wrapped(event) // wrapped handles event
  )
  // define the onAtom method on all tasks uniformly
  .allWithOnAtom { ctx ?=> atom =>
    ctx.state.get("ser_op") match
      case Some(SER_OP(...)) => // execute ser_op
        ctx.state.remove("ser_op") // remove ser_op from the state
        ... // execute ser_op
        ctx.emit(atom)
      case None => // do nothing, no op registered
        ctx.emit(atom)
  }

// ##################### How to send ser updates / ingest ser_ops into system

val ref = ... // get reference to workflow

ref ! "hello world world hello"

// option 1-2: encapsulate the SER_OP within its own atom
ref ! Fuse // fuse the workflow
ref ! SER_OP(op_data) // submit the update request
ref ! Fuse // fuse the workflow, forms an atom with a single event the SER_OP

// option 3:
ref ! FUSE
ref ! "hello world world hello"
ref ! SER_OP(op_data) // submit the udpate request
ref ! "hallo welt welt hallo"
ref ! FUSE // SER_OP is executed at time of FUSE
```