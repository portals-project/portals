package portals.compiler

import scala.annotation.experimental

import portals.application.*
import portals.compiler.compilers.*
import portals.compiler.physicalplan.*

/** Factory for creating compilers. */
private[portals] object CompilerBuilder:
  /** Compiler that will run all semantic checks before the code
    * transformations.
    */
  def preCompiler(): Compiler[Application, Application] = PreCheckCompiler()

  /** Compiler that will rewrite portals into sequencers and splitters. */
  @experimental 
  def portalRewriteCompiler(): Compiler[Application, Application] = PortalRewriteCompiler()

  /** Create a compiler that will run all checks and transform the code to its
    * physical representation.
    */
  def compiler(): Compiler[Application, PhysicalPlan[_]] = BaseCompiler()
