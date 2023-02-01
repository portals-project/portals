// package portals

// // /** TaskContext */
// // private[portals] trait TaskContext[T, U]
// //     // extends GenericTaskContext[T, U]
// //     extends EmittingTaskContext[T, U]
// //     with StatefulTaskContext[T, U]
// //     with LoggingTaskContext[T, U]:
//   //////////////////////////////////////////////////////////////////////////////
//   // Base Operations
//   //////////////////////////////////////////////////////////////////////////////

  
//   // def state: TaskState[Any, Any]

  
//   // def emit(event: U): Unit

  
//   // def log: Logger

//   //////////////////////////////////////////////////////////////////////////////
//   // Execution Context
//   //////////////////////////////////////////////////////////////////////////////

//   // /** The Path of this task */
//   // // has to be var so that it can be swapped at runtime
//   // private[portals] var path: String

//   // /** Contextual key for per-key execution */
//   // // has to be var so that it can be swapped at runtime
//   // private[portals] var key: Key[Int]

//   // /** The `SystemContext` that this task belongs to */
//   // // has to be var so that it can be swapped at runtime
//   // private[portals] var system: PortalsSystem

// // object TaskContext:
// //   def apply[T, U](): TaskContextImpl[T, U] = new TaskContextImpl[T, U]
