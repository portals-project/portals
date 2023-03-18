package portals.compiler

/** Sub-phase for the compiler, not a subclass of compiler phase by design.
  * Cannot be used as a normal compiler phase.
  */
private[portals] trait CompilerSubPhase[T, U] {
  self =>
  def run(t: T)(using ctx: CompilerContext): U
  def andThen[V](next: CompilerSubPhase[U, V]): CompilerSubPhase[T, V] =
    new CompilerSubPhase[T, V] {
      override def run(t: T)(using ctx: CompilerContext): V =
        next.run { self.run(t) }
    }
}

object CompilerSubPhase:
  /** Empty compiler phase, can be used to start a chain of compiler phases. */
  def empty[T]: CompilerSubPhase[T, T] = new CompilerSubPhase[T, T] {
    override def run(t: T)(using ctx: CompilerContext): T = t
  }

  /** Compiler phase that maps with the provided function `f`. */
  def map[T, U](f: T => U): CompilerSubPhase[T, U] = new CompilerSubPhase[T, U] {
    override def run(t: T)(using ctx: CompilerContext): U = f(t)
  }
