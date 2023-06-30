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
    // To be implemented as a Portal Replier:
    // * Handles CDC events on its regular input, applies them, and outputs CDC
    //   events.
    // * Handles QueryRequest and replies with QueryResponse on its portal
    //   handler.
    // Operation mode:
    // 1. apply the incoming operation
    // 2. return the result, emit CDC if changes are made
