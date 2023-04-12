package portals.api.builder

import org.apache.calcite.sql.`type`.SqlTypeName
import portals.api.builder.{ApplicationBuilder, TaskBuilder}
import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.AtomicPortalRef
import portals.application.task.{AskerTaskContext, PerKeyState, PerTaskState, TaskStates}
import portals.system.Systems
import portals.util.Future

import java.util
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Collectors
import scala.annotation.experimental
import java.math.BigDecimal

sealed class SQLQueryEvent

case class SelectOp(tableName: String, key: Int) extends SQLQueryEvent

case class InsertOp(tableName: String, data: List[Any], key: Int) extends SQLQueryEvent

case class Result(status: String, data: Any)

class Book(id: Integer, title: String, year: Integer, author: Integer) {
  def toObjectArray: Array[Object] = Array[Object](id.asInstanceOf[Object], title.asInstanceOf[Object], year.asInstanceOf[Object], author.asInstanceOf[Object])
}

@experimental object SQL {
  def main(args: Array[String]): Unit = {
    import scala.collection.JavaConverters._

    val app = PortalsApp("app") {
      val builder = ApplicationBuilder("app")
      val portal = Portal[SQLQueryEvent, Result]("portal", qEvent => qEvent match
        case SelectOp(tableName, key) =>
          key
        case InsertOp(tableName, data, key) =>
          key
        case _ => 0
      )

      // return two SQL queries for each iterator
      val generator = Generators
        .fromIteratorOfIterators[String](List(
          List("INSERT INTO Book (id, title, \"year\", author) VALUES (1, 'Les Miserables', 1862, 0)").iterator,
          List("INSERT INTO Book (id, title, \"year\", author) VALUES (2, 'The Hunchback of Notre-Dame', 1829, 0)").iterator,
          List("INSERT INTO Book (id, title, \"year\", author) VALUES (3, 'The Last Day of a Condemned Man', 1829, 0)").iterator,
          List("INSERT INTO Book (id, title, \"year\", author) VALUES (4, 'The three Musketeers', 1844, 1)").iterator,
          List("INSERT INTO Book (id, title, \"year\", author) VALUES (5, 'The Count of Monte Cristo', 1884, 1)").iterator,
          List("SELECT * FROM Book WHERE \"year\" > 1855 AND id IN (4, 5, 6)").iterator,
          List("INSERT INTO Book (id, title, \"year\", author) VALUES (6, 'The Lord of the Rings', 1954, 1)").iterator,
          List("SELECT * FROM Book WHERE \"year\" > 1855 AND id IN (4, 5, 6)").iterator,
        ).iterator)

      val calcite = new Calcite()
      calcite.registerTable("Book", List(SqlTypeName.INTEGER, SqlTypeName.VARCHAR, SqlTypeName.INTEGER, SqlTypeName.INTEGER).asJava,
        List("id", "title", "year", "author").asJava, 0)

      Workflows[String, Nothing]("askerWf")
        .source(generator.stream)
        .asker[Nothing](portal) {
          sql => {
            val futureReadyCond = new LinkedBlockingQueue[Integer]()
            val awaitForFutureCond = new LinkedBlockingQueue[Integer]()
            val awaitForFinishCond = new LinkedBlockingQueue[Integer]()
            val result = new util.ArrayList[Array[Object]]()
            val futures = new util.ArrayList[FutureWithResult]()

            var portalFutures = List[Future[Result]]()

            calcite.getTable("Book").setInsertRow(data => {
              val future = ask(portal)(InsertOp("Book", data.toList, data(0).asInstanceOf[Int]))
              portalFutures = portalFutures :+ future
              new FutureWithResult(future, null)
            })
            calcite.getTable("Book").setGetFutureByRowKeyFunc(key => {
              val future = ask(portal)(SelectOp("Book", key.asInstanceOf[BigDecimal].toBigInteger.intValueExact()))
              portalFutures = portalFutures :+ future
              new FutureWithResult(future, null)
            })

            calcite.executeSQL(sql,
              futureReadyCond, awaitForFutureCond, awaitForFinishCond, futures, result);

            futureReadyCond.take

            awaitAll(portalFutures: _*) {
              futures.forEach(f => {
                val data = f.future.asInstanceOf[Future[Result]].value
                f.futureResult = f.future.asInstanceOf[Future[Result]].value.get.data.asInstanceOf[Array[Object]]
              })
              awaitForFutureCond.put(1)
              awaitForFinishCond.take()

              println("====== Result for " + sql + " ======")
              result.forEach(row => println(util.Arrays.toString(row)))
            }
          }
        }

        .sink()
        .freeze()

      lazy val state = PerKeyState[Book]("book", null)

      Workflows[Nothing, Nothing]("bookWf")
        .source(Generators.empty.stream)
        .replier[Nothing](portal) {

          _ => ()
        } {
          q =>
            q match
              case SelectOp(tableName, key) =>
                val data = state.get()
                println("select " + key + " " + data)
                if (data != null)
                  reply(Result("ok", state.get().toObjectArray))
                else
                  reply(Result("ok", null))
              case InsertOp(tableName, data, key) =>
                println("insert " + data)
                state.set(Book(
                  data(0).asInstanceOf[Integer],
                  data(1).asInstanceOf[String],
                  data(2).asInstanceOf[Integer],
                  data(3).asInstanceOf[Integer]))
                println("inserted " + state.get())
                reply(Result("ok", Array[Object]()))
              case _ => reply(Result("error", List()))
        }

        .sink()
        .freeze()


    }

    val system = Systems.interpreter()
    system.launch(app)
    system.stepUntilComplete()
    system.shutdown()
  }

}
