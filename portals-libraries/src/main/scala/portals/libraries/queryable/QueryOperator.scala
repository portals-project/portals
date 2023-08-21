package portals.libraries.queryable

import portals.api.dsl.DSL.*
import portals.application.task.*
import portals.libraries.queryable.SQLParser.*
import portals.libraries.queryable.Types.*

/** Operator factory that implements the query operator. */
private[queryable] object QueryOperator:

  /** The task type that is returned by this operator. */
  private type _Type[T <: RowType] = GenericTask[SQLQueryRequest, SQLQueryResult, TableRequest[T], TableResponse[T]]

  /** Handler for SQL Query events. */
  private def handleSQLQuery[T <: RowType](req: SQLQueryRequest)(tablerefs: TableRef[T]*)(using
      AskerTaskContext[SQLQueryRequest, SQLQueryResult, TableRequest[T], TableResponse[T]]
  ): Unit =
    // * Handles Strings as inputs, then parses and validates them, and then
    //   executes them. The result is emitted as a string.
    // * Queries use the asker interface, to ask the corresponding Table portal,
    //   this sends a QueryRequest to the portal.
    // * If a query is malformed, it should output an Error as a response.
    // 1. parses the incoming query (String)
    // 2. validates the parsed query (table exists, correct form, etc.)
    // 3. rewrites the query / reorders it so it can be executed
    // 4. execute/interpret the query, intermediate state needs to be persisted
    parse(req.query) match
      case Some(Select(Asterisk, Table(table), Some(Predicate(col, Operator("="), Value(value))))) =>
        tablerefs.find(_.name == table) match
          case Some(tableref) =>
            val q = Get[T](extractKey(value))
            val f = ctx.ask(tableref.portal)(q)
            ctx.await(f):
              emit(SQLQueryResult(f.value.get))
          case None =>
            emit(SQLQueryResult(Error("Invalid table")))
      case _ =>
        emit(SQLQueryResult(Error("Invalid query")))

  /** Query operator factory. Connects to the provided `table`. The query
    * operator consumes SQL queries as strings, queries the corresponding
    * tables, and produces and emits the final output as a string.
    */
  def apply[T <: RowType](tables: TableRef[T]*): _Type[T] =
    Tasks.asker(tables.map(_.portal): _*): //
      e => //
        handleSQLQuery(e)(tables: _*)
