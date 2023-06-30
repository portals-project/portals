package portals.libraries.queryable

import portals.api.builder.FlowBuilder
import portals.libraries.queryable.Types.*

/** DSL entry point for the Queryable library. */
object Queryable:
  extension [T, U, CT, X <: RowType](fb: FlowBuilder[T, U, CT, CDC[X]])
    /** extension method for the FlowBuilder which can create a table. */
    def table(table: Table[X]): FlowBuilder[T, U, CDC[X], CDC[X]] =
      fb.task(TableOperator(table))

  extension [T, U, CT, X <: RowType](fb: FlowBuilder[T, U, CT, String])
    /** extension method for the FlowBuilder which can create a query. */
    def query(table: TableRef[X]): FlowBuilder[T, U, String, String] =
      fb.task(QueryOperator(table))
