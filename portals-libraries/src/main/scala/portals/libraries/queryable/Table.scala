package portals.libraries.queryable

import portals.libraries.queryable.Types.*

/** Table factory. */
object TableFactory:
  /** Creates a table for rows of type `T` for the provided `name`. */
  def apply[T <: RowType](name: String): TableType[T] = TableImpl(name)

  /** Internal API. A table for rows of type `T` with a `name`. */
  private[queryable] class TableImpl[T <: RowType](name: String) extends TableType[T]:
    override def ref: TableRef[T] = ???
