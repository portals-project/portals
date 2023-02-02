package portals

trait Future[T]:
  val id: Int

  def value: AskerTaskContext[_, _, _, T] ?=> Option[T]

  override def toString(): String = "Future(id=" + id + ")"

private[portals] class FutureImpl[T](override val id: Int) extends Future[T]:
  private lazy val _futures: AskerTaskContext[_, _, _, T] ?=> PerTaskState[Map[Int, T]] =
    PerTaskState[Map[Int, T]]("futures", Map.empty)

  override def value: AskerTaskContext[_, _, _, T] ?=> Option[T] =
    this._futures.get().get(id)

private[portals] object Future:
  private val _rand = new scala.util.Random
  private def generate_id: Int = { _rand.nextInt() }

  def apply[T](): Future[T] = new FutureImpl[T](generate_id)
