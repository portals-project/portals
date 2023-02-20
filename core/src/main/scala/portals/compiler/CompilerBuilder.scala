package portals.compiler

import portals.*
import portals.compiler.physicalplan.*

/** Factory for creating compilers. */
private[portals] object CompilerBuilder:
  /** Create a pre-compiler that will run all semantic checks before the code transformations. */
  def preCompiler(): Compiler[Application, Application] = PreCheckCompilerImpl()

  /** Create a compiler that will run all checks and transform the code to its physical representation. */
  def compiler(): Compiler[Application, PhysicalPlan[_]] = CompilerImpl()
