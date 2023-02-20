package portals.compiler

private[portals] trait CompilerPhase[T, U] {
  self =>

  def run(t: T)(using ctx: CompilerContext): U

  def andThen[V](next: CompilerPhase[U, V]): CompilerPhase[T, V] =
    new CompilerPhase[T, V] {
      override def run(t: T)(using ctx: CompilerContext): V =
        next.run { self.run(t) }
    }
}
