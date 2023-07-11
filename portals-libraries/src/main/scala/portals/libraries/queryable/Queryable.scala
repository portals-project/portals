package portals.libraries.queryable

import portals.api.builder.ApplicationBuilder
import portals.api.builder.FlowBuilder
import portals.libraries.queryable.Types.*

/** DSL entry point for the Queryable library. */
object Queryable:
  extension [T, U, CT, X <: RowType](fb: FlowBuilder[T, U, CT, CDC[X]])
    /** extension method for the FlowBuilder which can create a table. */
    def table(table: TableType[X]): FlowBuilder[T, U, CDC[X], CDC[X]] =
      fb
        .key(keyFrom(_))
        .task(TableOperator(table))

  extension [T, U, CT, X <: RowType](fb: FlowBuilder[T, U, CT, SQLQueryRequest])
    /** extension method for the FlowBuilder which can create a query. */
    def query(table: TableRef[X]*): FlowBuilder[T, U, SQLQueryRequest, SQLQueryResult] =
      fb.task(QueryOperator(table: _*))

  /** Creates a table for rows of type `T` for the provided `name`. */
  def Table[T <: RowType](name: String)(using ApplicationBuilder): TableType[T] =
    TableFactory[T](name)
