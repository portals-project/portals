package portals

trait PortalBuilder:
  def portal[T, R](name: String): AtomicPortalRef[T, R]
  def portal[T, R](name: String, f: T => Long): AtomicPortalRef[T, R]

object PortalBuilder:
  def apply(name: String)(using bctx: ApplicationBuilderContext): PortalBuilder =
    val _name = bctx.name_or_id(name)
    new PortalBuilderImpl(_name)

class PortalBuilderImpl(name: String)(using bctx: ApplicationBuilderContext) extends PortalBuilder:
  override def portal[T, R](name: String): AtomicPortalRef[T, R] =
    val path = bctx.app.path + "/portals/" + name
    val portal = AtomicPortal[T, R](path)
    bctx.addToContext(portal)
    AtomicPortalRef[T, R](portal)

  override def portal[T, R](name: String, f: T => Long): AtomicPortalRef[T, R] =
    val path = bctx.app.path + "/portals/" + name
    val portal = AtomicPortal[T, R](path, Some(f))
    bctx.addToContext(portal)
    AtomicPortalRef[T, R](portal)
