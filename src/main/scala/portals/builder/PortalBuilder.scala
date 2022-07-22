package portals

trait PortalBuilder:
  def portal[T, R](name: String): AtomicPortalRef[T, R]

object PortalBuilder:
  def apply()(using bctx: ApplicationBuilderContext): PortalBuilder = new PortalBuilderImpl()

class PortalBuilderImpl(using bctx: ApplicationBuilderContext) extends PortalBuilder:
  def portal[T, R](name: String): AtomicPortalRef[T, R] =
    val path = bctx.app.path + "/portals/" + name
    val portal = AtomicPortal[T, R](path, name)
    bctx.addToContext(portal)
    AtomicPortalRef[T, R](portal)
