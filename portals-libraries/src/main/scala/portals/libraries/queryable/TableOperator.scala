package portals.libraries.queryable

import portals.api.dsl.DSL.*
import portals.application.task.*
import portals.libraries.queryable.Types.*

/** Operator factory that implements the table operator. */
private[queryable] object TableOperator:

  /** The task type that is returned by this operator. */
  type _Type[T <: RowType] = GenericTask[CDC[T], CDC[T], TableRequest[T], TableResponse[T]]

  /** Handle CDC stream events and update the internal table state. */
  private def handleCDC[T <: RowType](cdc: CDC[T])(using
      StatefulTaskContext with EmittingTaskContext[CDC[T]]
  ): Unit =
    val state = PerKeyState[Option[T]]("state", None)
    cdc match {
      case Insert(v) =>
        state.set(Some(v))
        emit(cdc)
      case Remove(v) =>
        state.set(None)
        emit(cdc)
      case Update(v) =>
        state.set(Some(v))
        emit(cdc)
    }

  /** Handle Portal request events, respond, update internal state, and emit any
    * internal changes as CDC events.
    */
  private def handlePortal[T <: RowType](req: TableRequest[T])(using
      ReplierTaskContext[CDC[T], CDC[T], TableRequest[T], TableResponse[T]]
  ): Unit =
    val state = PerKeyState[Option[T]]("state", None)
    req match
      // get the state for the current key, else return error
      case Get(key) =>
        state.get() match
          case Some(v) => reply(GetResult(key, v))
          case None => reply(Error(s"Key: $key not found in table"))

      // set the state for the current key, emit changes as CDC event
      case Set(key, value) =>
        state.get() match
          case Some(v) =>
            state.set(Some(value))
            ctx.emit(Update(value))
            ctx.reply(SetResult(key))
          case None =>
            state.set(Some(value))
            ctx.emit(Insert(value))
            ctx.reply(SetResult(key))

  /** Table operator factory. This operator serves the table. It consumes change
    * data capture events, applies them to the corresponding tables, and
    * produces and emits outputs in the form of change data capture events. It
    * also handles internal SQL query requests from Query Operators by
    * responding to the request. Any changes to the table are reflected in the
    * emitted CDC.
    */
  def apply[T <: RowType](table: TableType[T]): _Type[T] =
    Tasks.replier[CDC[T], CDC[T], TableRequest[T], TableResponse[T]](table.portal) { //
      cdc =>
        handleCDC(cdc)
    } { //
      req =>
        handlePortal(req)
    }
