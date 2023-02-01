// package portals

// /** MapTaskContext, context without emit, etc. To be used in Map, FlatMap, etc., where the task is not supposed to emit.
//   * The internal context `_ctx` should be swapped at runtime, as the runtime may swap the context.
//   */
// private[portals] trait MapTaskContext[T, U]
//     extends GenericTaskContext[T, U]
//     with StatefulTaskContext[T, U]
//     with LoggingTaskContext[T, U]
