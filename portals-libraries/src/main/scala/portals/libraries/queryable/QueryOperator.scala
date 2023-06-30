package portals.libraries.queryable

import portals.application.task.*
import portals.libraries.queryable.Types.*

/** Operator factory that implements the query operator. */
private[queryable] object QueryOperator:

  /** The task type that is returned by this operator. */
  private type _Type[T <: RowType] = GenericTask[String, String, QueryRequest[T], QueryResponse[T]]

  /** Query operator factory. Connects to the provided `table`. The query
    * operator consumes SQL queries as strings, queries the corresponding
    * tables, and produces and emits the final output as a string.
    */
  def apply[T <: RowType](table: TableRef[T]*): _Type[T] =
    ??? // return a task that implements the query operator
