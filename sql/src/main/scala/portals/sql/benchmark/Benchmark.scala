package portals.sql.benchmark

import java.util
import java.util.concurrent.LinkedBlockingQueue

import scala.annotation.experimental

import org.apache.calcite.sql.`type`.SqlTypeName
import org.apache.calcite.util.NlsString

import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.application.generator.Generator
import portals.application.task.PerKeyState
import portals.runtime.WrappedEvents
import portals.runtime.WrappedEvents.WrappedEvent
import portals.sql.*
import portals.system.InterpreterSystem
import portals.system.Systems
import portals.util.Future
import portals.util.Key

import cask.*

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
//    println("body: " + request.text())
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

  object User extends DBSerializable[User]:
    def fromObjectArray(arr: List[Any]): User =
      User(
        arr(0).asInstanceOf[Integer],
        arr(1).asInstanceOf[String],
        arr(2).asInstanceOf[String],
        arr(3).asInstanceOf[String],
        arr(4).asInstanceOf[String],
        arr(5).asInstanceOf[String],
        arr(6).asInstanceOf[String],
        arr(7).asInstanceOf[String],
        arr(8).asInstanceOf[String],
        arr(9).asInstanceOf[String],
        arr(10).asInstanceOf[String]
      )

    def toObjectArray(user: User): Array[Object] =
      Array[Object](
        user.id.asInstanceOf[Object],
        user.field0.asInstanceOf[Object],
        user.field1.asInstanceOf[Object],
        user.field2.asInstanceOf[Object],
        user.field3.asInstanceOf[Object],
        user.field4.asInstanceOf[Object],
        user.field5.asInstanceOf[Object],
        user.field6.asInstanceOf[Object],
        user.field7.asInstanceOf[Object],
        user.field8.asInstanceOf[Object],
        user.field9.asInstanceOf[Object]
      )

  case class User(
      id: Integer,
      field0: String,
      field1: String,
      field2: String,
      field3: String,
      field4: String,
      field5: String,
      field6: String,
      field7: String,
      field8: String,
      field9: String
  ) {
    override def toString: String =
      s"User($id, $field0, $field1, $field2, $field3, $field4, $field5, $field6, $field7, $field8, $field9)"
  }

  def initApp() = {
    import scala.jdk.CollectionConverters.*

    import org.slf4j.LoggerFactory

    import ch.qos.logback.classic.Level
    import ch.qos.logback.classic.Logger

    import portals.api.dsl.ExperimentalDSL.*
    import portals.sql.*

    val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    logger.setLevel(Level.INFO)

    val app = PortalsApp("app") {
      val table = QueryableWorkflow.createTable[User]("Userr", "id", User)

      inputChan = new REPLGenerator()
      val generator = Generators.generator[String](inputChan)

      Workflows[String, String]("askerWf")
        .source(generator.stream)
        ._querier(table)(false)
        .sink()
        .freeze()
    }

    intSys = Systems.interpreter()
    intSys.launch(app)
    intSys.stepUntilComplete()
  }

//  def initApp() = {
//    import portals.api.dsl.ExperimentalDSL.*
//
//    import scala.jdk.CollectionConverters.*
//
//    val tableName = "Userr"
//
//    val calcite = new Calcite()
//    calcite.printPlan = false
//    calcite.registerTable(
//      tableName,
//      List(
//        SqlTypeName.VARCHAR,
//        SqlTypeName.VARCHAR,
//        SqlTypeName.VARCHAR,
//        SqlTypeName.VARCHAR,
//        SqlTypeName.VARCHAR,
//        SqlTypeName.VARCHAR,
//        SqlTypeName.VARCHAR,
//        SqlTypeName.VARCHAR,
//        SqlTypeName.VARCHAR,
//        SqlTypeName.VARCHAR,
//        SqlTypeName.VARCHAR
//      ).asJava,
//      List(
//        "id",
//        "field0",
//        "field1",
//        "field2",
//        "field3",
//        "field4",
//        "field5",
//        "field6",
//        "field7",
//        "field8",
//        "field9"
//      ).asJava,
//      0
//    )
//
//    val app = PortalsApp("app") {
//      val portal = Portal[SQLQueryEvent, SQLQueryResult](
//        "portal",
//        qEvent =>
//          qEvent match
//            case SelectOp(tableName, key) =>
//              key.hashCode
//            case InsertOp(tableName, data, key) =>
//              key.hashCode
//            case _ => 0
//      )
//
//      inputChan = new REPLGenerator()
//      val generator = Generators.generator[String](inputChan)
//
//      Workflows[String, Nothing]("queryWf")
//        .source(generator.stream)
//        .logger("queryWf")
//        .asker[Nothing](portal) { sql =>
//          val futureReadyCond = new LinkedBlockingQueue[Integer]()
//          val awaitForFutureCond = new LinkedBlockingQueue[Integer]()
//          val awaitForFinishCond = new LinkedBlockingQueue[Integer]()
//          val tableScanCntCond = new LinkedBlockingQueue[Integer]()
//          val result = new util.ArrayList[Array[Object]]()
//          val futures = new util.ArrayList[FutureWithResult]()
//          var portalFutures = List[Future[SQLQueryResult]]()
//
//          calcite
//            .getTable(tableName)
//            .setInsertRow(data => {
//              val key = data(0).asInstanceOf[String]
//              val future = ask(portal)(InsertOp("User", data.toList, key))
//              portalFutures = portalFutures :+ future
//              new FutureWithResult(future, null)
//            })
//          calcite
//            .getTable(tableName)
//            .setGetFutureByRowKeyFunc(key => {
//              val keyStr = key.asInstanceOf[NlsString].getValue
//              println("User key: " + keyStr)
//              val future =
//                ask(portal)(SelectOp("User", keyStr))
//              portalFutures = portalFutures :+ future
//              new FutureWithResult(future, null)
//            })
//
//          calcite.executeSQL(
//            sql,
//            futureReadyCond,
//            awaitForFutureCond,
//            awaitForFinishCond,
//            tableScanCntCond,
//            futures,
//            result
//          )
//
//          futureReadyCond.take
//          println("future asking done, waiting for future result")
//
//          awaitAll[SQLQueryResult](portalFutures: _*) {
//            futures.forEach(f => {
//              f.futureResult = f.future.asInstanceOf[Future[SQLQueryResult]].value.get.result
//            })
//
//            println("future result ready, start execution")
//            awaitForFutureCond.put(1) // allow SQL execution to start
//            awaitForFinishCond.take() // wait for SQL execution to finish
//
//            resultStr = util.Arrays.deepToString(result.toArray)
//            println(resultStr)
////              result.forEach(row => println(util.Arrays.toString(row)))
//          }
//
////            val future = ask(portal)(SelectOp("user", 1))
////
////            future.await {
////              println(future.value.get)
////            }
//        }
//        .sink()
//        .freeze()
//
//      lazy val state = PerKeyState[User]("user", null)
//
//      Workflows[Nothing, Nothing]("dataWf")
//        .source(Generators.empty.stream)
//        .replier[Nothing](portal) { _ =>
//          ()
//        } { q =>
//          q match
//            case SelectOp(tableName, key) =>
//              val data = state.get()
//              println("select " + key + " " + data)
//              if (data != null)
//                reply(SQLQueryResult("ok", state.get().toObjectArray))
//              else
//                reply(SQLQueryResult("ok", null))
//            case InsertOp(tableName, data, key) =>
//              println("insert " + data)
//              state.set(
//                User(
//                  data(0).asInstanceOf[String],
//                  data(1).asInstanceOf[String],
//                  data(2).asInstanceOf[String],
//                  data(3).asInstanceOf[String],
//                  data(4).asInstanceOf[String],
//                  data(5).asInstanceOf[String],
//                  data(6).asInstanceOf[String],
//                  data(7).asInstanceOf[String],
//                  data(8).asInstanceOf[String],
//                  data(9).asInstanceOf[String],
//                  data(10).asInstanceOf[String],
//                )
//              )
//              println("inserted " + state.get())
//              reply(SQLQueryResult("ok", Array[Object]()))
//            case _ => reply(SQLQueryResult("error", null))
//        }
//        .sink()
//        .freeze()
//    }
//
//    intSys = Systems.interpreter()
//    intSys.launch(app)
//    intSys.stepUntilComplete()
//  }
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
