package portals.libraries.queryable

/** The types used by the queryable library. */
object Types:
  /** The row type of a table. */
  trait RowType extends Serializable with Product

  /** The type of a table. */
  sealed trait TableType[T <: RowType]

  /** Change data capture events handled by the table operators. */
  sealed trait CDC[T]
  case class Insert[T <: RowType](v: T) extends CDC[T]
  case class Remove[T <: RowType](v: T) extends CDC[T]
  case class Update[T <: RowType](v: T) extends CDC[T]

  /** The type of a query request handled by a table. */
  sealed trait QueryRequest[T <: RowType]

  /** The type of a query response returned by a table. */
  sealed trait QueryResponse[T <: RowType]
