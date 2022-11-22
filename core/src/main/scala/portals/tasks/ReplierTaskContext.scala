package portals

trait ReplierTaskContext[T, U, Req, Rep] extends TaskContext[T, U]:
  /** should be var so that it can be swapped out during runtime */
  private[portals] var id: Int = _
  private[portals] var asker: String = _
  private[portals] var portal: String = _
  private[portals] var portalAsker: String = _

  def reply(r: Rep): Unit

object ReplierTaskContext:
  def fromTaskContext[T, U, Req, Rep](
      ctx: TaskContext[T, U],
      tcb: TaskCallback[T, U, Req, Rep]
  ): ReplierTaskContext[T, U, Req, Rep] =
    new ReplierTaskContext[T, U, Req, Rep] {
      //////////////////////////////////////////////////////////////////////////
      // ReplierContext
      //////////////////////////////////////////////////////////////////////////
      override def reply(r: Rep): Unit =
        tcb.reply(r, this.portal, this.portalAsker, this.path, this.asker, ctx.key, id)

      //////////////////////////////////////////////////////////////////////////
      // TaskContext
      //////////////////////////////////////////////////////////////////////////
      override def emit(event: U): Unit = ctx.emit(event)
      var key: Key[Int] = ctx.key
      override def log: Logger = ctx.log
      var path: String = ctx.path
      override def state: TaskState[Any, Any] = ctx.state
      var system: PortalsSystem = ctx.system
    }
