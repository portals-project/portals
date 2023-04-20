package portals.sql

import ch.qos.logback.classic.Logger
import org.apache.calcite.sql.`type`.SqlTypeName
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import portals.api.builder.{ApplicationBuilder, TaskBuilder}
import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.application.AtomicPortalRef
import portals.application.task.{AskerTaskContext, PerKeyState, PerTaskState, TaskStates}
import portals.sql.*
import portals.system.Systems
import portals.test.TestUtils
import portals.util.Future

import java.math.BigDecimal
import java.util
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Collectors
import scala.annotation.experimental
import scala.collection.immutable.List

@experimental
//@RunWith(classOf[JUnit4])
object SQLCleaned extends App {

//  @Test
//  def main(): Unit = {
    import ch.qos.logback.classic.{Level, Logger}
    import org.slf4j.LoggerFactory
    import portals.api.dsl.ExperimentalDSL.*

    import scala.jdk.CollectionConverters.*

    val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    logger.setLevel(Level.INFO)

    val tester = new TestUtils.Tester[String]()

    val app = PortalsApp("app") {
      val bookTable = QueryableWorkflow.createTable[Book]("Book", "id", Book)
      val authorTable = QueryableWorkflow.createTable[Author]("Author", "id", Author)

      // return two SQL queries for each iterator
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
        .id()
        .querier(bookTable, authorTable)
        .task(tester.task)
        .sink()
        .freeze()
    }

    val system = Systems.interpreter()
    system.launch(app)
    system.stepUntilComplete()
    system.shutdown()

    // inserts
    for (i <- 1 to 7) {
      tester.receiveAssert("[1]")
    }

    // book table query
    tester.receiveAssert("[5, The Count of Monte Cristo, 1884, 1]")
    tester.receiveAssert("[1]")
    tester.receiveAssert("[5, The Count of Monte Cristo, 1884, 1]")
    tester.receiveAssert("[6, The Lord of the Rings, 1954, 1]")

    // author table query
    tester.receiveAssert("[0, Victor, Hugo]")
    tester.receiveAssert("[1, Alexandre, Dumas]")

    // join query
    tester.receiveAssert("[6, The Lord of the Rings, 1954, Alexandre Dumas]")
    tester.receiveAssert("[5, The Count of Monte Cristo, 1884, Alexandre Dumas]")
    tester.receiveAssert("[4, The three Musketeers, 1844, Alexandre Dumas]")
    tester.receiveAssert("[1, Les Miserables, 1862, Victor Hugo]")
  }

