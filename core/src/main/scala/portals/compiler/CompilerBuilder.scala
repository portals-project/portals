package portals.compiler

import portals.*
import portals.compiler.physicalplan.*

private[portals] object CompilerBuilder:
  def preCompiler(): Compiler[Application, Application] = PreCheckCompilerImpl()
  def compiler(): Compiler[Application, PhysicalPlan[_]] = CompilerImpl()
