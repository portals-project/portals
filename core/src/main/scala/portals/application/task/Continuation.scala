package portals.application.task

type Continuation[T, U, Req, Rep] = AskerTaskContext[T, U, Req, Rep] ?=> Unit
