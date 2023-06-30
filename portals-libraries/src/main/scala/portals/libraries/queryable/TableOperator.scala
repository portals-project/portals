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
  def apply[T <: RowType](table: Table[T]): _Type[T] =
    ??? // return a task that implements the table operator
