package portals.compiler

/** Phase of the compiler that transforms an input of type `T` into an output of
  * type `U`.
  *
  * @tparam T
  *   input type
  * @tparam U
  *   output type
  */
private[portals] trait CompilerPhase[T, U] {
  self =>

  /** Run the compiler phase on input `t`.
    *
    * @param t
    *   input to be transformed
    * @param ctx
    *   compiler context
    * @return
    *   the transformed output
    */
  def run(t: T)(using ctx: CompilerContext): U

  /** Compose this compiler phase with another `next` compiler phase.
    *
    * @param next
    *   next compiler phase
    * @tparam V
    *   output type of the next compiler phase
    * @return
    *   a new compiler phase that first runs this compiler phase and then the
    *   next compiler phase in sequence
    */
  def andThen[V](next: CompilerPhase[U, V]): CompilerPhase[T, V] =
    new CompilerPhase[T, V] {
      override def run(t: T)(using ctx: CompilerContext): V =
        next.run { self.run(t) }
    }
}
