package portals.sql.example

import scala.annotation.experimental

import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.api.dsl.DSL.PortalsApp
import portals.sql.DBSerializable
import portals.sql.QueryableWorkflow
import portals.system.Systems

object Book extends DBSerializable[Book]:
  def fromObjectArray(arr: List[Any]): Book =
    Book(
      arr(0).asInstanceOf[Integer],
      arr(1).asInstanceOf[String],
      arr(2).asInstanceOf[Integer],
      arr(3).asInstanceOf[Integer]
    )

  def toObjectArray(book: Book): Array[Object] =
    Array[Object](
      book.id.asInstanceOf[Object],
      book.title.asInstanceOf[Object],
      book.year.asInstanceOf[Object],
      book.author.asInstanceOf[Object]
    )

case class Book(id: Integer, title: String, year: Integer, author: Integer) {
  override def toString: String = s"Book($id, $title, $year, $author)"
}

object Author extends DBSerializable[Author]:
  def fromObjectArray(arr: List[Any]): Author =
    Author(arr(0).asInstanceOf[Integer], arr(1).asInstanceOf[String], arr(2).asInstanceOf[String])

  def toObjectArray(author: Author): Array[Object] =
    Array[Object](author.id.asInstanceOf[Object], author.fname.asInstanceOf[Object], author.lname.asInstanceOf[Object])

case class Author(id: Integer, fname: String, lname: String) {
  override def toString: String = s"Author($id, $fname, $lname)"
}

@experimental
object SQLExample extends App:

  import scala.jdk.CollectionConverters.*

  import org.slf4j.LoggerFactory

  import ch.qos.logback.classic.Level
  import ch.qos.logback.classic.Logger

  import portals.api.dsl.ExperimentalDSL.*
  import portals.sql.*

  val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
  logger.setLevel(Level.INFO)

  val app = PortalsApp("app") {
    val bookTable = QueryableWorkflow.createTable[Book]("Book", "id", Book)
    val authorTable = QueryableWorkflow.createTable[Author]("Author", "id", Author)

    val generator = Generators
      .fromIteratorOfIterators[String](
        List(
          List("INSERT INTO Author (id, fname, lname) VALUES (0, 'Victor', 'Hugo')").iterator,
          List("INSERT INTO Author (id, fname, lname) VALUES (1, 'Alexandre', 'Dumas')").iterator,
          List("INSERT INTO Book (id, title, \"year\", author) VALUES (1, 'Les Miserables', 1862, 0)").iterator,
          List(
            "INSERT INTO Book (id, title, \"year\", author) VALUES (2, 'The Hunchback of Notre-Dame', 1829, 0)"
          ).iterator,
          List(
            "INSERT INTO Book (id, title, \"year\", author) VALUES (3, 'The Last Day of a Condemned Man', 1829, 0)"
          ).iterator,
          List("INSERT INTO Book (id, title, \"year\", author) VALUES (4, 'The three Musketeers', 1844, 1)").iterator,
          List(
            "INSERT INTO Book (id, title, \"year\", author) VALUES (5, 'The Count of Monte Cristo', 1884, 1)"
          ).iterator,
          List("SELECT * FROM Book WHERE \"year\" > 1855 AND id IN (4, 5, 6)").iterator,
          List(
            "INSERT INTO Book (id, title, \"year\", author) VALUES (6, 'The Lord of the Rings', 1954, 1)"
          ).iterator,
          List("SELECT * FROM Book WHERE \"year\" > 1855 AND id IN (4, 5, 6)").iterator,
          List("SELECT * FROM Author WHERE id IN (0, 1)").iterator,
          List(
            "SELECT b.id, b.title, b.\"year\", a.fname || ' ' || a.lname FROM Book b\n" +
              "JOIN Author a ON b.author=a.id\n" +
              "WHERE b.\"year\" > 1830 AND a.id IN (0, 1) AND b.id IN (1, 2, 3, 4, 5, 6)\n" +
              "ORDER BY b.id DESC"
          ).iterator,
        ).iterator
      )

    Workflows[String, String]("askerWf")
      .source(generator.stream)
      .querier(bookTable, authorTable)
      .sink()
      .freeze()
  }

  val system = Systems.interpreter()
  system.launch(app)
  system.stepUntilComplete()
  system.shutdown()
