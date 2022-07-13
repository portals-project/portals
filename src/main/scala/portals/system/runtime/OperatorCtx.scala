package portals

trait OperatorCtx[T, U]:
  def submit(item: U): Unit
  def fuse(): Unit
  def seal(): Unit
end OperatorCtx