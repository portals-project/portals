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

@experimental
//@RunWith(classOf[JUnit4])
object TransactionCleaned extends App {

//  @Test
//  def main(): Unit = {
    import ch.qos.logback.classic.{Level, Logger}
    import org.slf4j.LoggerFactory
    import portals.api.dsl.ExperimentalDSL.*

    import scala.jdk.CollectionConverters.*

    val tester = new TestUtils.Tester[String]()

    val app = PortalsApp("app") {
      val bookTable = QueryableWorkflow.createTable[Book]("Book", "id", Book)
      val authorTable = QueryableWorkflow.createTable[Author]("Author", "id", Author)

      // return two SQL queries for each iterator
      val generator = Generators.fromList(
        List(
          "INSERT INTO Author (id, fname, lname) VALUES (0, 'Victor', 'Hugo')",
          "SELECT * FROM Author WHERE id IN (0, 1)",
//          "INSERT INTO Author (id, fname, lname) VALUES (1, 'Alexandre', 'Dumas')",
        )
      )

      Workflows[String, String]("askerWf")
        .source(generator.stream)
        .querierTransactional(bookTable, authorTable)
        .task(tester.task)
        .sink()
        .freeze()
    }

    val system = Systems.interpreter()
    system.launch(app)
    system.stepUntilComplete()
    system.shutdown()

    tester.receiveAssert("[1]") // affected rows for insert
    tester.receiveAssert("rollback")
  }
