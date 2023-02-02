package portals

import portals.*

private[portals] trait Compiler[T, U]:
  def compile(t: T): U
end Compiler // trait

private[portals] object Compiler:
  def apply(): Compiler[Application, Application] = CompilerImpl()
