package portals.sql.example

import scala.annotation.experimental

import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.api.dsl.DSL.PortalsApp
import portals.sql.DBSerializable
import portals.sql.QueryableWorkflow
import portals.system.Systems

/** Simulate concurrent queries, in this case insert on key 0 will block select
  * on key (1, 0), precommit on key 1 will be rolled back
  */
@experimental
object TransactionalConflict extends App:

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

    val generator = Generators.fromList(
      List(
        "INSERT INTO Author (id, fname, lname) VALUES (0, 'Victor', 'Hugo')",
        "SELECT * FROM Author WHERE id IN (1, 0)",
      )
    )

    Workflows[String, String]("askerWf")
      .source(generator.stream)
      .querierTransactional(bookTable, authorTable)
      .sink()
      .freeze()
  }

//  val system = Systems.interpreter()
  val system = new RandomInterpreter()
  system.launch(app)
  system.stepUntilComplete()
  system.shutdown()
