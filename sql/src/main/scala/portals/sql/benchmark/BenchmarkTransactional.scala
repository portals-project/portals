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
object Server1 extends MainRoutes {

  var inputChan: REPLGenerator = _
  var intSys: InterpreterSystem = _
  var resultStr: String = ""

  @get("/")
  def stat() = {
    s"parsing: ${CalciteStat.getAvgParsingTime}\n" +
      s"validation: ${CalciteStat.getAvgValidationTime}\n" +
      s"planning: ${CalciteStat.getAvgPlanningTime}\n" +
      s"execution: ${CalciteStat.getAvgExecutionTime}\n"
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
        .querierTransactional(table)
        .sink()
        .freeze()
    }

    intSys = Systems.interpreter()
    intSys.launch(app)
    intSys.stepUntilComplete()
  }
}

