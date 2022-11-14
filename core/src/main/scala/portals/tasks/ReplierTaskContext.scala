package portals

trait ReplierTaskContext[T, U, Req, Rep] extends TaskContext[T, U]:
  private[portals] var id: Int = -1
  def reply(r: Rep): Unit

object ReplierTaskContext:
  def fromTaskContext[T, U, Req, Rep](
      ctx: TaskContext[T, U]
  )(portalcb: PortalTaskCallback[T, U, Req, Rep], portal: String): ReplierTaskContext[T, U, Req, Rep] =
    new ReplierTaskContext[T, U, Req, Rep] {
      // ReplierContext
      override def reply(r: Rep): Unit =
        portalcb.reply(r)(ctx.key, id)

      // TaskContext
      override def emit(event: U): Unit = ctx.emit(event)
      var key: Key[Int] = ctx.key
      override def log: Logger = ctx.log
      var path: String = ctx.path
      override def state: TaskState[Any, Any] = ctx.state
      var system: PortalsSystem = ctx.system
    }
