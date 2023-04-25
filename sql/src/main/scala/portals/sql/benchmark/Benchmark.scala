package portals.sql.benchmark

import cask.*
import org.apache.calcite.sql.`type`.SqlTypeName
import org.apache.calcite.util.NlsString
import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.application.generator.Generator
import portals.application.task.PerKeyState
import portals.runtime.WrappedEvents
import portals.runtime.WrappedEvents.WrappedEvent
import portals.sql.*
import portals.system.{InterpreterSystem, Systems}
import portals.util.{Future, Key}

import java.util
import java.util.concurrent.LinkedBlockingQueue
import scala.annotation.experimental

@experimental
object Server extends MainRoutes {

  var inputChan: REPLGenerator = _
  var intSys: InterpreterSystem = _
  var resultStr: String = ""

  @get("/")
  def hello() = {
    "hello"
  }

  @post("/query")
  def query(request: Request) = {
    resultStr = ""
    val queryText = request.text()
    println("body: " + request.text())
    inputChan.add(queryText)
    intSys.stepUntilComplete()
    resultStr
  }

  initApp()
  // TODO: init query wf and data wf
  initialize()

  sealed class SQLQueryEvent
  case class SelectOp(tableName: String, key: String) extends SQLQueryEvent
  case class InsertOp(tableName: String, data: List[Any], key: String) extends SQLQueryEvent
  case class SQLQueryResult(msg: String, result: Array[Object])

  def initApp() = {
    import portals.api.dsl.ExperimentalDSL.*

    import scala.jdk.CollectionConverters.*

    val tableName = "Userr"

    val calcite = new Calcite()
    calcite.printPlan = false
    calcite.registerTable(
      tableName,
      List(
        SqlTypeName.VARCHAR,
        SqlTypeName.VARCHAR,
        SqlTypeName.VARCHAR,
        SqlTypeName.VARCHAR,
        SqlTypeName.VARCHAR,
        SqlTypeName.VARCHAR,
        SqlTypeName.VARCHAR,
        SqlTypeName.VARCHAR,
        SqlTypeName.VARCHAR,
        SqlTypeName.VARCHAR,
        SqlTypeName.VARCHAR
      ).asJava,
      List(
        "id",
        "field0",
        "field1",
        "field2",
        "field3",
        "field4",
        "field5",
        "field6",
        "field7",
        "field8",
        "field9"
      ).asJava,
      0
    )

    val app = PortalsApp("app") {
      val portal = Portal[SQLQueryEvent, SQLQueryResult](
        "portal",
        qEvent =>
          qEvent match
            case SelectOp(tableName, key) =>
              key.hashCode
            case InsertOp(tableName, data, key) =>
              key.hashCode
            case _ => 0
      )

      inputChan = new REPLGenerator()
      val generator = Generators.generator[String](inputChan)

      Workflows[String, Nothing]("queryWf")
        .source(generator.stream)
        .logger("queryWf")
        .asker[Nothing](portal) { sql =>
          val futureReadyCond = new LinkedBlockingQueue[Integer]()
          val awaitForFutureCond = new LinkedBlockingQueue[Integer]()
          val awaitForFinishCond = new LinkedBlockingQueue[Integer]()
          val tableScanCntCond = new LinkedBlockingQueue[Integer]()
          val result = new util.ArrayList[Array[Object]]()
          val futures = new util.ArrayList[FutureWithResult]()
          var portalFutures = List[Future[SQLQueryResult]]()

          calcite
            .getTable(tableName)
            .setInsertRow(data => {
              val key = data(0).asInstanceOf[String]
              val future = ask(portal)(InsertOp("User", data.toList, key))
              portalFutures = portalFutures :+ future
              new FutureWithResult(future, null)
            })
          calcite
            .getTable(tableName)
            .setGetFutureByRowKeyFunc(key => {
              val keyStr = key.asInstanceOf[NlsString].getValue
              println("User key: " + keyStr)
              val future =
                ask(portal)(SelectOp("User", keyStr))
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
          )

          futureReadyCond.take
          println("future asking done, waiting for future result")

          awaitAll[SQLQueryResult](portalFutures: _*) {
            futures.forEach(f => {
              f.futureResult = f.future.asInstanceOf[Future[SQLQueryResult]].value.get.result
            })

            println("future result ready, start execution")
            awaitForFutureCond.put(1) // allow SQL execution to start
            awaitForFinishCond.take() // wait for SQL execution to finish

            resultStr = util.Arrays.deepToString(result.toArray)
            println(resultStr)
//              result.forEach(row => println(util.Arrays.toString(row)))
          }

//            val future = ask(portal)(SelectOp("user", 1))
//
//            future.await {
//              println(future.value.get)
//            }
        }
        .sink()
        .freeze()

      lazy val state = PerKeyState[User]("user", null)

      Workflows[Nothing, Nothing]("dataWf")
        .source(Generators.empty.stream)
        .replier[Nothing](portal) { _ =>
          ()
        } { q =>
          q match
            case SelectOp(tableName, key) =>
              val data = state.get()
              println("select " + key + " " + data)
              if (data != null)
                reply(SQLQueryResult("ok", state.get().toObjectArray))
              else
                reply(SQLQueryResult("ok", null))
            case InsertOp(tableName, data, key) =>
              println("insert " + data)
              state.set(
                User(
                  data(0).asInstanceOf[String],
                  data(1).asInstanceOf[String],
                  data(2).asInstanceOf[String],
                  data(3).asInstanceOf[String],
                  data(4).asInstanceOf[String],
                  data(5).asInstanceOf[String],
                  data(6).asInstanceOf[String],
                  data(7).asInstanceOf[String],
                  data(8).asInstanceOf[String],
                  data(9).asInstanceOf[String],
                  data(10).asInstanceOf[String],
                )
              )
              println("inserted " + state.get())
              reply(SQLQueryResult("ok", Array[Object]()))
            case _ => reply(SQLQueryResult("error", null))
        }
        .sink()
        .freeze()
    }

    intSys = Systems.interpreter()
    intSys.launch(app)
    intSys.stepUntilComplete()
  }
}

class REPLGenerator extends Generator[String]:
  val _iter = new REPL().iterator

  override def generate(): WrappedEvent[String] =
    val nxt = _iter.next()
    WrappedEvents.Event(Key(nxt.hashCode()), nxt)

  override def hasNext(): Boolean = _iter.hasNext

  def add(q: String): Unit = _iter.enqueue(q)

class REPL:

  class BlockingQueueIterator[T] extends Iterator[T] {
    val queue = new LinkedBlockingQueue[T]()

    override def hasNext: Boolean = !queue.isEmpty

    override def next(): T = queue.take()

    def enqueue(e: T) = queue.offer(e)
  }

  val iterator = new BlockingQueueIterator[String]()
  val waitForMsg = new LinkedBlockingQueue[Int]()
  val waitForProcessing = new LinkedBlockingQueue[Int]()
  var requestCnt = 0
  var prefix = ""

  def setPrefix(s: String): Unit = prefix = s
