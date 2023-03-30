package portals.util

import scala.collection.mutable.ListBuffer

import portals.application.task.AskerTaskContext
import portals.application.task.PerTaskState

/** A future is a value that may be completed and available at a later time.
  *
  * Futures are created by requesting a value from a portal. The value is
  * completed by a corresponding reply from the portal. The `await` method
  * registers a continuation on the future that is executed when the future is
  * completed.
  *
  * @example
  *   {{{
  * val future = ask(portal, request)
  * await(future) { future.value.get match
  *   case Rep(value) =>
  *     log.info(value.toString())
  * }
  *   }}}
  *
  * @tparam T
  *   the type of the value
  */
trait Future[T]:
  /** Internal API. The unique identifier of the future. Used to match against
    * replies to complete the future and execute the continuation.
    */
  private[portals] val id: Int

  /** The value of the future. If the future is not yet completed, the value is
    * `None`. If the future is completed, the value is `Some(value)`. Try using
    * `await` instead to register a continuation for when the future is
    * completed.
    *
    * @return
    *   the optional value of the future
    */
  def value: AskerTaskContext[_, _, _, T] ?=> Option[T]

  override def toString(): String = "Future(id=" + id + ")"

/** Internal API. Implementation of the [[Future]] trait. */
private[portals] class FutureImpl[T](override val id: Int) extends Future[T]:
  // TODO: should be a global shared value instead to ensure that it is consistent with others
  private lazy val _futures: AskerTaskContext[_, _, _, T] ?=> PerTaskState[Map[Int, T]] =
    PerTaskState[Map[Int, T]]("futures", Map.empty)

  override def value: AskerTaskContext[_, _, _, T] ?=> Option[T] =
    this._futures.get().get(id)

/** Internal API. Future factory. */
private[portals] object Future:
  private val _rand = new scala.util.Random
  private def generate_id: Int = { _rand.nextInt() }

  def apply[T](): Future[T] = new FutureImpl[T](generate_id)

object Futures:
  def awaitAll[Rep](
      futures: Future[Rep]*
  )(f: AskerTaskContext[_, _, _, Rep] ?=> List[Rep] => Unit)(using ctx: AskerTaskContext[_, _, _, Rep]): Unit = {
    var n = 0
    val resp = ListBuffer[Rep]()
    futures.foreach(future =>
      ctx.await(future) {
        n += 1
        resp += future.value.get
        if n == futures.length then f(resp.toList)
      }
    )
  }
