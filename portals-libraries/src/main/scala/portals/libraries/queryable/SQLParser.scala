package portals.libraries.queryable

/** Dummy parser just for initial testing purposes. */
object SQLParser:
  sealed trait SQLAST
  case class Select(columns: Columns, from: Table, where: Option[Predicate]) extends SQLAST
  sealed trait Columns extends SQLAST
  case class ColumnsList(columns: Seq[Column]) extends Columns
  case class Column(name: String) extends SQLAST
  case object Asterisk extends Columns
  case class Table(name: String) extends SQLAST
  case class Predicate(column: Column, operator: Operator, value: Value) extends SQLAST
  case class Operator(op: String) extends SQLAST
  case class Value(value: String) extends SQLAST

  def parse(query: String): Option[SQLAST] = query match
    case "SELECT * FROM authors WHERE name = 'John'" =>
      Some(Select(Asterisk, Table("authors"), Some(Predicate(Column("name"), Operator("="), Value("John")))))
    case "SELECT * FROM authors WHERE name = 'Jane'" =>
      Some(Select(Asterisk, Table("authors"), Some(Predicate(Column("name"), Operator("="), Value("Jane")))))
    case "SELECT * FROM authors WHERE name = 'Jule'" =>
      Some(Select(Asterisk, Table("authors"), Some(Predicate(Column("name"), Operator("="), Value("Jule")))))
