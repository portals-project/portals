package portals.libraries.queryable

import java.sql.RowId

import portals.application.*

/** The types used by the queryable library. */
object Types:
  /** A key extractor used in various contexts. */
  def extractKey(v: Any): Long = v match
    case v: Long => v
    case v: Int => v.toLong
    case v: Any => v.hashCode().toLong

  /** The row type of a table, necessarily has arity more than 1. */
  trait RowType extends Serializable with Product with Product1[Any]:
    /** Get the key of a row, automatically computed from the first element. */
    def key: Long = extractKey(this.productElement(0))

  /** The type of a table. */
  trait TableType[T <: RowType]:
    /** Get a reference of this table. */
    def ref: TableRef[T]

    /** Get a reference of the `portal` for this table. */
    private[portals] def portal: AtomicPortalRef[TableRequest[T], TableResponse[T]]

  /** A reference to a table. */
  trait TableRef[T <: RowType]:
    /** Get a reference of the `portal` for this table. */
    private[portals] def portal: AtomicPortalRef[TableRequest[T], TableResponse[T]]

  /** Change data capture events handled by the table operators. */
  sealed trait CDC[T <: RowType] extends Serializable
  case class Insert[T <: RowType](v: T) extends CDC[T]
  case class Remove[T <: RowType](v: T) extends CDC[T]
  case class Update[T <: RowType](v: T) extends CDC[T]

  /** The type of a query request handled by a table. */
  sealed trait TableRequest[T <: RowType] extends Serializable
  case class Get[T <: RowType](key: Long) extends TableRequest[T]
  case class Set[T <: RowType](key: Long, value: T) extends TableRequest[T]

  /** The type of a query response returned by a table. */
  sealed trait TableResponse[+T <: RowType] extends Serializable
  case class GetResult[T <: RowType](key: Long, value: T) extends TableResponse[T]
  case class SetResult[T <: RowType](key: Long) extends TableResponse[T]
  case class Error(msg: String) extends TableResponse[Nothing]

  /** The type of an SQL Query. */
  case class SQLQuery(query: String) extends Serializable
  type SQLQueryRequest = SQLQuery // ALIAS

  /** The type of a SQL Query Response. */
  case class SQLQueryResult(result: Any) extends Serializable

  /** Key extraction method for TableRequests. */
  def keyFrom[T <: RowType](req: TableRequest[T]) =
    req match
      case Get(key) => key
      case Set(key, _) => key

  /** Key extraction method for CDC events. */
  def keyFrom[T <: RowType](cdc: CDC[T]) =
    cdc match
      case Insert(v) => v.key
      case Remove(v) => v.key
      case Update(v) => v.key
