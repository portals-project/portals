package portals.sql

import java.math.BigDecimal
import java.util
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Collectors

import scala.annotation.experimental

import org.apache.calcite.sql.`type`.SqlTypeName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Test

import ch.qos.logback.classic.Logger

import portals.api.builder.ApplicationBuilder
import portals.api.builder.TaskBuilder
import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.application.task.AskerTaskContext
import portals.application.task.PerKeyState
import portals.application.task.PerTaskState
import portals.application.task.TaskStates
import portals.application.AtomicPortalRef
import portals.sql.*
import portals.system.Systems
import portals.test.TestUtils
import portals.util.Future

sealed class SQLQueryEvent

case class SelectOp(tableName: String, key: Int) extends SQLQueryEvent

case class InsertOp(tableName: String, data: List[Any], key: Int) extends SQLQueryEvent

case class Result(status: String, data: Any)

class Book(id: Integer, title: String, year: Integer, author: Integer) {
  def toObjectArray: Array[Object] = Array[Object](
    id.asInstanceOf[Object],
    title.asInstanceOf[Object],
    year.asInstanceOf[Object],
    author.asInstanceOf[Object]
  )

  override def toString: String = s"Book($id, $title, $year, $author)"
}

class Author(id: Integer, fname: String, lname: String) {
  def toObjectArray: Array[Object] =
    Array[Object](id.asInstanceOf[Object], fname.asInstanceOf[Object], lname.asInstanceOf[Object])

  override def toString: String = s"Author($id, $fname, $lname)"
}

@experimental
@RunWith(classOf[JUnit4])
class Main {

  @Test
  def main(): Unit = {
    import portals.api.dsl.ExperimentalDSL.*

    import scala.jdk.CollectionConverters.*

    import org.slf4j.LoggerFactory
    import ch.qos.logback.classic.Logger
    import ch.qos.logback.classic.Level

    val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    logger.setLevel(Level.INFO)

    val tester = new TestUtils.Tester[String]()

    val app = PortalsApp("app") {
      val bookPortal = Portal[SQLQueryEvent, Result](
        "bookPortal",
        qEvent =>
          qEvent match
            case SelectOp(tableName, key) =>
              key
            case InsertOp(tableName, data, key) =>
              key
            case _ => 0
      )
      val authorPortal = Portal[SQLQueryEvent, Result](
        "authorPortal",
        qEvent =>
          qEvent match
            case SelectOp(tableName, key) =>
              key
            case InsertOp(tableName, data, key) =>
              key
            case _ => 0
      )

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

      val calcite = new Calcite()
      calcite.printPlan = false
      calcite.registerTable(
        "Book",
        List(SqlTypeName.INTEGER, SqlTypeName.VARCHAR, SqlTypeName.INTEGER, SqlTypeName.INTEGER).asJava,
        List("id", "title", "year", "author").asJava,
        0
      )
      calcite.registerTable(
        "Author",
        List(SqlTypeName.INTEGER, SqlTypeName.VARCHAR, SqlTypeName.VARCHAR).asJava,
        List("id", "fname", "lname").asJava,
        0
      )

      Workflows[String, String]("askerWf")
        .source(generator.stream)
        .asker[String](bookPortal) { sql =>

          val futureReadyCond = new LinkedBlockingQueue[Integer]()
          val awaitForFutureCond = new LinkedBlockingQueue[Integer]()
          val awaitForFinishCond = new LinkedBlockingQueue[Integer]()
          val tableScanCntCond = new LinkedBlockingQueue[Integer]()
          val result = new util.ArrayList[Array[Object]]()
          val futures = new util.ArrayList[FutureWithResult]()

          var portalFutures = List[Future[Result]]()

          // needs to know how many times are select asking called for different tables
          calcite
            .getTable("Book")
            .setInsertRow(data => {
              val future = ask(bookPortal)(InsertOp("Book", data.toList, data(0).asInstanceOf[Int]))
              portalFutures = portalFutures :+ future
              new FutureWithResult(future, null)
            })
          calcite
            .getTable("Book")
            .setGetFutureByRowKeyFunc(key => {
              println("Book key: " + key)
              val future =
                ask(bookPortal)(SelectOp("Book", key.asInstanceOf[BigDecimal].toBigInteger.intValueExact()))
              portalFutures = portalFutures :+ future
              new FutureWithResult(future, null)
            })
          calcite
            .getTable("Author")
            .setInsertRow(data => {
              val future = ask(authorPortal)(InsertOp("Author", data.toList, data(0).asInstanceOf[Int]))
              portalFutures = portalFutures :+ future
              new FutureWithResult(future, null)
            })
          calcite
            .getTable("Author")
            .setGetFutureByRowKeyFunc(key => {
              println("Author key: " + key)
              val future =
                ask(authorPortal)(SelectOp("Author", key.asInstanceOf[BigDecimal].toBigInteger.intValueExact()))
              portalFutures = portalFutures :+ future
              new FutureWithResult(future, null)
            })

          calcite.executeSQL(
            sql,
            futureReadyCond,
            awaitForFutureCond,
            awaitForFinishCond,
            tableScanCntCond,
            futures,
            result
          );

          val tableScanCnt = tableScanCntCond.take
          println("tableScanCnt: " + tableScanCnt)

          val emit = {
            (x: String) => ctx.emit(x)
          }

          for (i <- 1 to tableScanCnt) {
            println("try future ready consume")
            futureReadyCond.take
            println("future ready consume done")

            // wait for the last one to awaitAll
            if i != tableScanCnt then awaitForFutureCond.put(1)
            else
              awaitAll[Result](portalFutures: _*) {
                futures.forEach(f => {
                  val data = f.future.asInstanceOf[Future[Result]].value
                  f.futureResult = f.future.asInstanceOf[Future[Result]].value.get.data.asInstanceOf[Array[Object]]
                })

                awaitForFutureCond.put(1) // allow SQL execution to start

                awaitForFinishCond.take() // wait for SQL execution to finish

                println("====== Result for " + sql + " ======")
                result.forEach(row => println(util.Arrays.toString(row)))

                result.forEach(row => {
                  emit(util.Arrays.toString(row))
                })
              }

          }

        }
        .task(tester.task)
        .sink()
        .freeze()

      // =================== Book Wf

      lazy val bookState = PerKeyState[Book]("book", null)

      Workflows[Nothing, Nothing]("bookWf")
        .source(Generators.empty.stream)
        .replier[Nothing](bookPortal) { _ =>
          ()
        } { q =>
          q match
            case SelectOp(tableName, key) =>
              val data = bookState.get()
              println("select " + key + " " + data)
              if (data != null)
                reply(Result("ok", bookState.get().toObjectArray))
              else
                reply(Result("ok", null))
            case InsertOp(tableName, data, key) =>
              println("insert " + data)
              bookState.set(
                Book(
                  data(0).asInstanceOf[Integer],
                  data(1).asInstanceOf[String],
                  data(2).asInstanceOf[Integer],
                  data(3).asInstanceOf[Integer]
                )
              )
              println("inserted " + bookState.get())
              reply(Result("ok", Array[Object]()))
            case _ => reply(Result("error", List()))
        }
        .sink()
        .freeze()

      // ============== Author Wf

      lazy val authorState = PerKeyState[Author]("author", null)

      Workflows[Nothing, Nothing]("authorWf")
        .source(Generators.empty.stream)
        .replier[Nothing](authorPortal) { _ =>
          ()
        } { q =>
          q match
            case SelectOp(tableName, key) =>
              val data = authorState.get()
              println("select " + key + " " + data)
              if (data != null)
                reply(Result("ok", authorState.get().toObjectArray))
              else
                reply(Result("ok", null))
            case InsertOp(tableName, data, key) =>
              println("insert " + data)
              authorState.set(
                Author(data(0).asInstanceOf[Integer], data(1).asInstanceOf[String], data(2).asInstanceOf[String])
              )
              println("inserted " + authorState.get())
              reply(Result("ok", Array[Object]()))
            case _ => reply(Result("error", List()))
        }
        .sink()
        .freeze()
    }

    val system = Systems.interpreter()
    system.launch(app)
    Thread.sleep(200)
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

}
