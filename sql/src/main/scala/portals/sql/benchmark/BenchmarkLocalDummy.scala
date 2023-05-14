package portals.sql.benchmark

import cask.*
import org.apache.calcite.sql.`type`.SqlTypeName
import org.apache.calcite.util.NlsString
import portals.api.builder.WorkflowBuilder
import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.application.Workflow
import portals.application.generator.Generator
import portals.application.task.PerKeyState
import portals.runtime.WrappedEvents
import portals.runtime.WrappedEvents.WrappedEvent
import portals.runtime.interpreter.InterpreterRuntime
import portals.sql.*
import portals.system.{InterpreterSystem, Systems}
import portals.util.{Future, Key}

import java.util
import java.util.concurrent.LinkedBlockingQueue
import scala.annotation.experimental
import scala.util.Random

@experimental
object BenchmarkLocalDummy extends App {

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

  def run() = {

    val opPerLoop = 10000
    val loop = 1

    val base = "SELECT * FROM Userr WHERE id="
    val rnd = Random()

    for (i <- 1 to loop) {
      for (j <- 1 to opPerLoop) {
        val queryText = base + rnd.nextInt(10000)
        inputChan.add(queryText)
        opMap += (queryText -> System.nanoTime())
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

      inputChan = new REPLGenerator[String]()
      val generator = Generators.generator[String](inputChan)

      def genWf(name: String): Workflow[String, String] =
        Workflows[String, String]("askerWf" + name)
          .source(generator.stream)
//          .map(x => {
//            timeList = timeList :+ (System.nanoTime() - opMap(x))
//            x
//          })
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
//    println("op/s: " + timeList.length.toDouble * 1_000_000_000 / (edTime - stTime))
//    println("avg time: " + timeList.sum.toDouble / timeList.length / 1_000_000 + "ms")
//    println("50% time: " + timeListNPercentile(50).toDouble / 1_000_000 + "ms")
//    println("90% time: " + timeListNPercentile(90).toDouble / 1_000_000 + "ms")
//    println("99% time: " + timeListNPercentile(99).toDouble / 1_000_000 + "ms")
//    println(stat())
  }
}
