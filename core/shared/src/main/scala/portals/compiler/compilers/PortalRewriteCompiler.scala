package portals.compiler.compilers

import scala.annotation.experimental

import portals.application.Application
import portals.compiler.*
import portals.compiler.phases.*
import portals.compiler.physicalplan.*

/** Compiler that rewrites portals into sequencers and splitters. */
@experimental
private[portals] class PortalRewriteCompiler extends Compiler[Application, Application]:
  given ctx: CompilerContext = new CompilerContext()

  override def compile(application: Application): Application =
    CompilerPhase.empty
      .andThen(CompilerPhases.rewritePortal)
      .run(application)

end PortalRewriteCompiler // class
