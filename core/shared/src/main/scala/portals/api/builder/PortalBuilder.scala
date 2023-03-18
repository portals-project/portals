package portals.api.builder

import portals.application.*

/** Builder for portals.
  *
  * The PortalBuilder is used to build portals. A workflow can connect to a
  * portal via either an `asker` task or a `replier` task.
  *
  * Accessed from the application builder via `builder.portals`.
  *
  * @example
  *   {{{builder.portals.portal[String, Int]("portalName")}}}
  *
  * @example
  *   {{{builder.portals.portal[String, Int]("portalName", keyFrom)}}}
  */
trait PortalBuilder:
  /** Build a portal with `name` and no key-extractor.
    *
    * @example
    *   {{{builder.portals.portal[String, Int]("portalName")}}}
    *
    * @param name
    *   the name of the portal
    * @tparam T
    *   the type of the request
    * @tparam R
    *   the type of the response
    * @return
    *   the portal reference
    */
  def portal[T, R](name: String): AtomicPortalRef[T, R]

  /** Build a portal with `name` and a key-extractor `f`.
    *
    * @example
    *   {{{builder.portals.portal[String, Int]("portalName", keyFrom)}}}
    *
    * @param name
    *   the name of the portal
    * @param f
    *   the key-extractor function
    * @tparam T
    *   the type of the request
    * @tparam R
    *   the type of the response
    * @return
    *   the portal reference
    */
  def portal[T, R](name: String, f: T => Long): AtomicPortalRef[T, R]

/** Internal API. The portal builder. */
object PortalBuilder:
  /** Internal API. Create a PortalBuilder using the application context. */
  def apply(name: String)(using bctx: ApplicationBuilderContext): PortalBuilder =
    val _name = bctx.name_or_id(name)
    new PortalBuilderImpl(_name)

/** Internal API. Implementation of the PortalBuilder. */
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
