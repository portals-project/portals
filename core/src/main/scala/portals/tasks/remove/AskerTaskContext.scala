// package portals.tasks.remove

// import scala.annotation.experimental

// // trait AskerTaskContext[T, U, Req, Rep]
// //     extends TaskContext[T, U]
// //     with AskingTaskContext[T, U, Req, Rep]
// //     with AwaitingTaskContext[T, U, Req, Rep]
// // private[portals] val _continuations: PerTaskState[Map[Int, Continuation[T, U, Req, Rep]]]
// // private[portals] val _futures: PerTaskState[Map[Int, Rep]]
// // def ask(portal: AtomicPortalRefType[Req, Rep])(req: Req): Future[Rep]
// // def await(future: Future[Rep])(f: AskerTaskContext[T, U, Req, Rep] ?=> Unit): Unit

// // object AskerTaskContext:
// //   def fromTaskContext[T, U, Req, Rep](
// //       ctx: TaskContext[T, U],
// //       tcb: TaskCallback[T, U, Req, Rep]
// //   ): AskerTaskContext[T, U, Req, Rep] =
// //     new AskerTaskContext[T, U, Req, Rep] {
// //       //////////////////////////////////////////////////////////////////////////
// //       // AskerContext
// //       //////////////////////////////////////////////////////////////////////////
// //       override private[portals] val _continuations =
// //         PerTaskState[Map[Int, Continuation[T, U, Req, Rep]]]("continuations", Map.empty)(using ctx)
// //       override private[portals] val _futures =
// //         PerTaskState[Map[Int, Rep]]("futures", Map.empty)(using ctx)

// //       override def ask(portal: AtomicPortalRefKind[Req, Rep])(req: Req): Future[Rep] =
// //         val f: Future[Rep] = Future()
// //         tcb.ask(portal.path, ctx.path, req, ctx.key, f.id)
// //         f

// //       override def await(future: Future[Rep])(f: AskerTaskContext[T, U, Req, Rep] ?=> Unit): Unit =
// //         _continuations.update(future.asInstanceOf[FutureImpl[_]].id, f)

// //       //////////////////////////////////////////////////////////////////////////
// //       // TaskContext
// //       //////////////////////////////////////////////////////////////////////////
// //       override def emit(event: U): Unit = ctx.emit(event)
// //       var key: Key[Int] = ctx.key
// //       override def log: Logger = ctx.log
// //       var path: String = ctx.path
// //       override def state: TaskState[Any, Any] = ctx.state
// //       var system: PortalsSystem = ctx.system
// //     }
