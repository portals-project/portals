package portals.libraries.queryable

import portals.api.builder.ApplicationBuilder
import portals.application.*
import portals.libraries.queryable.Types.*

/** Table factory. */
object TableFactory:
  /** Creates a table for rows of type `T` for the provided `name`. */
  def apply[T <: RowType](name: String)(using apb: ApplicationBuilder): TableType[T] =
    // this creates a portal for the table which is hidden to the user
    val portal = apb.portals.portal[TableRequest[T], TableResponse[T]](name, keyFrom(_))
    TableImpl(name, portal)

  /** Internal API. A table for rows of type `T` with a `name`. */
  private[queryable] class TableImpl[T <: RowType](
      name: String,
      portalref: AtomicPortalRef[TableRequest[T], TableResponse[T]]
  ) extends TableType[T]:
    override def ref: TableRef[T] = new TableRef[T] { def portal = portalref }
    override private[portals] def portal: AtomicPortalRef[TableRequest[T], TableResponse[T]] = portalref
