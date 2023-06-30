package portals.libraries.queryable

import portals.application.task.*
import portals.libraries.queryable.Types.*

/** Operator factory that implements the table operator. */
private[queryable] object TableOperator:

  /** The task type that is returned by this operator. */
  type _Type[T <: RowType] = GenericTask[CDC[T], CDC[T], QueryRequest[T], QueryResponse[T]]

  /** Table operator factory. This operator serves the table. It consumes change
    * data capture events, applies them to the corresponding tables, and
    * produces and emits outputs in the form of change data capture events. It
    * also handles internal SQL query requests from Query Operators by
    * responding to the request. Any changes to the table are reflected in the
    * emitted CDC.
    */
  def apply[T <: RowType](table: TableType[T]): _Type[T] =
    ??? // return a task that implements the table operator
    // To be implemented as a Portal Asker:
    // * Handles Strings as inputs, then parses and validates them, and then
    //   executes them. The result is emitted as a string.
    // * Queries use the asker interface, to ask the corresponding Table portal,
    //   this sends a QueryRequest to the portal.
    // * If a query is malformed, it should output an Error as a response.
    // 1. parses the incoming query (String)
    // 2. validates the parsed query (table exists, correct form, etc.)
    // 3. rewrites the query / reorders it so it can be executed
    // 4. execute/interpret the query, intermediate state needs to be persisted
