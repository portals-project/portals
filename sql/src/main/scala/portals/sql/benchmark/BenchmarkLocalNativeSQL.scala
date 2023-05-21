package portals.sql.benchmark

import java.util
import java.util.concurrent.LinkedBlockingQueue

import scala.annotation.experimental
import scala.util.Random

import org.apache.calcite.sql.`type`.SqlTypeName
import org.apache.calcite.util.NlsString

import portals.api.builder.WorkflowBuilder
import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.application.generator.Generator
import portals.application.task.PerKeyState
import portals.application.task.PerTaskState
import portals.application.Workflow
import portals.runtime.interpreter.InterpreterRuntime
import portals.runtime.WrappedEvents
import portals.runtime.WrappedEvents.WrappedEvent
import portals.sql.*
import portals.system.InterpreterSystem
import portals.system.Systems
import portals.util.Future
import portals.util.Key

import cask.*

@experimental
object BenchmarkLocalNativeSQL extends App {

  var inputChan: REPLGenerator[String] = _
  var intSys: InterpreterSystem = _
  var resultStr: String = ""

  var opMap = Map[String, Long]()
  var timeList = List[Long]()

  def stat() = {
    s"parsing: ${CalciteStat.getAvgParsingTime}\n" +
      s"validation: ${CalciteStat.getAvgValidationTime}\n" +
      s"planning: ${CalciteStat.getAvgPlanningTime}\n" +
      s"execution: ${CalciteStat.getAvgExecutionTime}\n"
  }

  val opPerLoop = 100
  val loop = 10000

  def run() = {

    val base = "SELECT * FROM Userr WHERE id="
    val rnd = Random()

    var cnt = 0
    for (i <- 1 to loop) {
      for (j <- 1 to opPerLoop) {
        val queryText = base + cnt
        cnt += 1
        inputChan.add(queryText)
        if (rnd.nextInt(1000) == 0) {
          opMap += (queryText -> System.nanoTime())
        }
      }
      intSys.stepUntilComplete()
    }
  }

  initApp()
  runAll()

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

  def timeListNPercentile(n: Int): Long = {
    val sorted = timeList.sorted
    sorted(sorted.length * n / 100)
  }

  def initApp() = {
    import ch.qos.logback.classic.{Level, Logger}
    import org.slf4j.LoggerFactory
    import portals.api.dsl.ExperimentalDSL.*
    import portals.sql.*

    import java.math.BigDecimal
    import scala.jdk.CollectionConverters.*

    val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    logger.setLevel(Level.INFO)

//    var input: REPLGenerator = null

    val app = PortalsApp("app") {
      val table = QueryableWorkflow.createTable[User]("Userr", "id", User)

      inputChan = new REPLGenerator()
      val generator = Generators.generator[String](inputChan)

      def genWf(name: String): Workflow[String, String] =
        Workflows[String, String]("askerWf" + name)
          .source(generator.stream)
          .asker[String](table.portal) { sql =>
            val futureReadyCond = PersistentLinkedBlockingQueue[Integer]("futureReadyCond")
            val awaitForFutureCond = PersistentLinkedBlockingQueue[Integer]("awaitForFutureCond")
            val awaitForFinishCond = PersistentLinkedBlockingQueue[Integer]("awaitForFinishCond")
            val tableOptCntCond = PersistentLinkedBlockingQueue[Integer]("tableOptCntCond")
            val result = PersistentList[Array[Object]]("result")
            val futures = PersistentList[FutureWithResult]("futures")
            val portalFutures = PersistentList[Future[Result]]("portalFutures")

            val calcite = new Calcite()
//            calcite.parseSQL(sql)
//            calcite.printPlan = false
//
            val ti = table
            calcite.registerTable(ti.tableName, ti.fieldTypes.toList.asJava, ti.fieldNames.toList.asJava, 0)
//            calcite.parseAndPlanLogic(sql)
            calcite.parseSQL(sql)

//
//            calcite.registerTable(ti.tableName, ti.fieldTypes.toList.asJava, ti.fieldNames.toList.asJava, 0)
//            calcite
//              .getTable(ti.tableName)
//              .setInsertRow(data => {
//                // TODO: assert pk always Int
//                val future = ask(ti.portal)(InsertOp(ti.tableName, data.toList, data(0).asInstanceOf[Int]))
//                portalFutures.add(future)
//                new FutureWithResult(future, null)
//              })
//            calcite
//              .getTable(ti.tableName)
//              .setGetFutureByRowKeyFunc(key => {
//                val future =
//                  ask(ti.portal)(SelectOp(ti.tableName, key.asInstanceOf[BigDecimal].toBigInteger.intValueExact()))
//                portalFutures.add(future)
//                new FutureWithResult(future, null)
//              })
//
//            calcite.executeSQL(
//              sql,
//              futureReadyCond,
//              awaitForFutureCond,
//              awaitForFinishCond,
//              tableOptCntCond,
//              futures,
//              result
//            )

//            val tableOptCnt = tableOptCntCond.take

            val future =
              ask(ti.portal)(SelectOp(ti.tableName, 1)) // 1 -> key
            portalFutures.add(future)

            val emit = { (x: String) =>
              ctx.emit(x)
            }

            // wait for the last one to awaitAll
            awaitAll[Result](portalFutures.asScala.toList: _*) {
              futures.forEach(f => {
                val data = f.future.asInstanceOf[Future[Result]].value
                f.futureResult = f.future.asInstanceOf[Future[Result]].value.get.data.asInstanceOf[Array[Object]]
              })

              emit(sql)

              if opMap.contains(sql) then timeList = timeList :+ (System.nanoTime() - opMap(sql))
            }
          }
          .sink()
          .freeze()

      genWf("1")
//      genWf("2")
    }

    intSys = Systems.interpreter()
    //    intSys = new RandomInterpreter(Some(1))
    intSys.launch(app)
  }

  def runAll() = {
    val stTime = System.nanoTime()
    run()
    val edTime = System.nanoTime()

    println("total time: " + (edTime - stTime).toDouble / 1_000_000_000 + "s")
    println("op/s: " + (opPerLoop * loop).toDouble * 1_000_000_000 / (edTime - stTime))
    println("avg time: " + timeList.sum.toDouble / timeList.length / 1_000_000 + "ms")
    println("50% time: " + timeListNPercentile(50).toDouble / 1_000_000 + "ms")
    println("90% time: " + timeListNPercentile(90).toDouble / 1_000_000 + "ms")
    println("99% time: " + timeListNPercentile(99).toDouble / 1_000_000 + "ms")
    println(stat())
  }
}
