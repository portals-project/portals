package portals

import scala.annotation.experimental

import portals.*

//////////////////////////////////////////////////////////////////////////////
// Experimental DSL
//////////////////////////////////////////////////////////////////////////////

/** Experimental API. Various mix of experimental API extensions. */
@experimental
object ExperimentalDSL:
  extension [T](splitter: AtomicSplitter[T]) {
    def split(f: T => Boolean)(using ab: ApplicationBuilder): AtomicStreamRef[T] =
      ab.splits.split(splitter, f)
  }

  extension (gb: GeneratorBuilder) {
    def empty[T]: AtomicGeneratorRef[T] = gb.fromList(List.empty)

    def signal[T](sig: T): AtomicGeneratorRef[T] = gb.fromList(List[T](sig))
  }

  extension [T, U, CT, CU](fb: FlowBuilder[T, U, CT, CU]) {
    def empty[NU](): FlowBuilder[T, U, CU, NU] = fb.flatMap(_ => List.empty[NU])
  }

  // same as empty :)
  extension [T, U, CT, CU](fb: FlowBuilder[T, U, CT, CU]) {
    def consume(): FlowBuilder[T, U, CU, CU] = fb.flatMap(_ => List.empty[CU])
  }

  extension [Rep](future: Future[Rep]) {
    def await[T, U, Req](using ctx: AskerTaskContext[T, U, Req, Rep])(
        f: AskerTaskContext[T, U, Req, Rep] ?=> Unit
    ): Unit =
      ctx.await(future)(f)
  }

  def await[T, U, Req, Rep](using
      ctx: AskerTaskContext[T, U, Req, Rep]
  )(future: Future[Rep])(f: AskerTaskContext[T, U, Req, Rep] ?=> Unit): Unit = ctx.await(future)(f)

  /** Used for fecursive functions from the Asker Task. Yes, here we do need to have the AskerTaskContext as an
    * implicit, otherwise it will crash.
    */
  private object Rec:
    def rec0[A, T, U, Req, Rep](
        f: (AskerTaskContext[T, U, Req, Rep] ?=> A) => AskerTaskContext[T, U, Req, Rep] ?=> A
    ): AskerTaskContext[T, U, Req, Rep] ?=> A = f(rec0(f))

    def rec1[A, B, T, U, Req, Rep](
        fRec: (AskerTaskContext[T, U, Req, Rep] ?=> A => B) => AskerTaskContext[T, U, Req, Rep] ?=> A => B
    ): AskerTaskContext[T, U, Req, Rep] ?=> A => B = fRec(rec1(fRec))

  extension [T, U, CT, CU, Req, Rep](pfb: FlowBuilder[T, U, CT, CU]#PortalFlowBuilder[Req, Rep]) {
    def askerRec[CCU](
        fRec: (
            AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit
        ) => AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit
    ): FlowBuilder[T, U, CU, CCU] =
      pfb.asker(fRec(Rec.rec1(fRec)))
  }

  def awaitRec[T, U, Req, Rep](using
      ctx: AskerTaskContext[T, U, Req, Rep]
  )(future: Future[Rep])(
      fRec: (AskerTaskContext[T, U, Req, Rep] ?=> Unit) => AskerTaskContext[T, U, Req, Rep] ?=> Unit
  ): Unit = ctx.await(future)(fRec(Rec.rec0(fRec)))

  extension [Rep](future: Future[Rep]) {
    def awaitRec[T, U, Req](using ctx: AskerTaskContext[T, U, Req, Rep])(
        fRec: (AskerTaskContext[T, U, Req, Rep] ?=> Unit) => AskerTaskContext[T, U, Req, Rep] ?=> Unit
    ): Unit =
      ctx.await(future)(fRec(Rec.rec0(fRec)))
  }

end ExperimentalDSL
