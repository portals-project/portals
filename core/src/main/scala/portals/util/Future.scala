package portals

trait Future[T]:
  val id: Int

  def value: AskerTaskContext[_, _, _, T] ?=> Option[T]

  override def toString(): String = "Future(id=" + id + ")"

private[portals] class FutureImpl[T](override val id: Int) extends Future[T]:

  override def value: AskerTaskContext[_, _, _, T] ?=> Option[T] =
    summon[AskerTaskContext[_, _, _, T]]._futures.get().get(id)

private[portals] object Future:
  private val _rand = new scala.util.Random
  private def generate_id: Int = { _rand.nextInt() }

  def apply[T](): Future[T] = new FutureImpl[T](generate_id)
