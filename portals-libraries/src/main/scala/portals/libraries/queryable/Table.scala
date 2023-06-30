package portals.libraries.queryable

import portals.libraries.queryable.Types.*

/** A reference to a table. */
sealed trait TableRef[T <: RowType] extends Serializable

/** A table for rows of type `T` with a `name`. */
class Table[T <: RowType](name: String):
  /** Returns a reference to a table. */
  def ref: TableRef[T] = ???
