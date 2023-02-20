package portals.compiler

import portals.*
import portals.compiler.physicalplan.*

/** Compiler that transforms an input of type `T` into an output of type `U`.
  *
  * @tparam T
  *   input type
  * @tparam U
  *   output type
  */
private[portals] trait Compiler[T, U]:

  /** Compile the input into an output.
    *
    * @param t
    *   input to be transformed
    * @return
    *   the transformed output
    */
  def compile(t: T): U
end Compiler // trait
