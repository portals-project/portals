package portals.api.dsl

import scala.annotation.experimental

import portals.api.builder.ApplicationBuilder
import portals.api.builder.FlowBuilder
import portals.api.builder.GeneratorBuilder
import portals.api.builder.TaskExtensions.*
import portals.api.dsl.DSL.Tasks
import portals.application.*
import portals.application.task.AskerTaskContext
import portals.application.task.MapTaskStateExtension.*
import portals.application.task.PerKeyState
import portals.application.task.PerTaskState
import portals.util.Future
import portals.util.FutureImpl

////////////////////////////////////////////////////////////////////////////////
// Experimental DSL
////////////////////////////////////////////////////////////////////////////////

/** Experimental API. Various mix of experimental API extensions. Not stable. */
object ExperimentalDSL:
  //////////////////////////////////////////////////////////////////////////////
  // Builder DSL
  //////////////////////////////////////////////////////////////////////////////
  extension [T](splitter: AtomicSplitterRefKind[T]) {
    // split a splitter by filter into a new stream.
    def split(f: T => Boolean)(using ab: ApplicationBuilder): AtomicStreamRef[T] =
      ab.splits.split(splitter, f)
  }

  extension (gb: GeneratorBuilder) {
    // create a generator with no output (to be used to create an empty stream)
    def empty[T]: AtomicGeneratorRef[T] = gb.fromList(List.empty)
  }

  // consume the events of a flow
  extension [T, U, CT, CU](fb: FlowBuilder[T, U, CT, CU])
    // consume events, change the type
    def empty[NU](): FlowBuilder[T, U, CU, NU] = fb.flatMap(_ => List.empty[NU])
    // consume events, keep the type
    def consume(): FlowBuilder[T, U, CU, CU] = fb.flatMap(_ => List.empty[CU])
    // consume events, change type to Nothing
    def nothing(): FlowBuilder[T, U, CU, Nothing] = fb.flatMap(_ => List.empty[Nothing])
    // sample every `n`th event
    def sample(n: Int): FlowBuilder[T, U, CU, CU] =
      val task =
        Tasks.init[CU, CU]:
          val _rand = scala.util.Random()
          inline def sample(): Boolean = _rand.nextInt(n) < 1
          Tasks.filter(_ => sample())
      fb.task(task)
  end extension

  //////////////////////////////////////////////////////////////////////////////
  // Recursive DSL
  //////////////////////////////////////////////////////////////////////////////
  /** Used for creating recursive functions. */
  private object Rec:
    def rec[A, B](f: (A => B) => A => B): A => B = f(rec(f))
    def contextual_rec[A, B](f: (A ?=> B) => A ?=> B): A ?=> B = f(contextual_rec(f))

  private[portals] class RecursiveAsker[T, U, CT, CU, CCU](fb: FlowBuilder[T, U, CT, CU]):
    def apply[Req, Rep](portals: AtomicPortalRefKind[Req, Rep]*)(
        fRec: (
            AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit
        ) => AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit
    ): FlowBuilder[T, U, CU, CCU] =
      fb.recursiveAsker[CCU, Req, Rep](portals: _*)(fRec)

  // Recursive extensions for the FlowBuilder.
  extension [T, U, CT, CU](fb: FlowBuilder[T, U, CT, CU]) {

    /** Shorthand for creating a recursive asker task.
      *
      * @example
      *   {{{
      * .recursiveAsker[Int] { self => x =>
      *   val future: Future[Pong] = ask(portal)(Ping(x))
      *   future.await {
      *     ctx.emit(future.value.get.x)
      *     if future.value.get.x > 0 then self(future.value.get.x)
      *   }
      * }
      *   }}}
      *
      * @param fRec
      *   The recursive function.
      */
    def recursiveAsker[CCU, Req, Rep](portals: AtomicPortalRefKind[Req, Rep]*)(
        fRec: (
            AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit
        ) => AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit
    ): FlowBuilder[T, U, CU, CCU] =
      fb.asker[CCU, Req, Rep](portals: _*) { fRec(Rec.contextual_rec(fRec)) }

    def recursiveAsker[CCU]: RecursiveAsker[T, U, CT, CU, CCU] = new RecursiveAsker(fb)
  }

  /** Shorthand for creating a recursive await. */
  def awaitRec[T, U, Req, Rep](
      future: Future[Rep]
  )(fRec: (AskerTaskContext[T, U, Req, Rep] ?=> Unit) => AskerTaskContext[T, U, Req, Rep] ?=> Unit)(using
      ctx: AskerTaskContext[T, U, Req, Rep]
  ): Unit =
    ctx.await(future)(fRec(Rec.contextual_rec(fRec)))

  def awaitAll[Rep](
      futures: Future[Rep]*
  )(f: AskerTaskContext[_, _, _, Rep] ?=> Unit)(using ctx: AskerTaskContext[_, _, _, Rep]): Unit = {
    lazy val _futures =
      PerTaskState[Map[Int, Rep]]("futures", Map.empty)
    lazy val _futures_copy =
      PerTaskState[Map[Int, Rep]]("futures_copy", Map.empty)
    lazy val _count =
      PerKeyState[Map[Int, Int]]("_count", Map.empty)

    _count.update(futures(0).id, 0)

    val wrappedFunc: Future[Rep] => Unit = { future =>
      _count.update(futures(0).id, _count.get(futures(0).id).get + 1)
      _futures_copy.update(future.id, future.value.get)
      if _count.get(futures(0).id).get == futures.length then
        // restore futures
        futures.foreach(future =>
          _futures.update(future.id, _futures_copy.get(future.id).get)
          _futures_copy.remove(future.id)
        )
        f
        // clear state
        futures.foreach(future => _futures.remove(future.id))
        _count.remove(futures(0).id)
    }

    futures.foreach(future => ctx.await(future)(wrappedFunc(future)))
  }

end ExperimentalDSL