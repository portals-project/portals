package portals.compiler

import portals.*
import portals.compiler.physicalplan.*

private[portals] trait Compiler[T, U]:
  def compile(t: T): U
end Compiler // trait
