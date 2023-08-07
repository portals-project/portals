package portals.libraries.sql.examples.sqltodataflow

import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.libraries.sql.*
import portals.libraries.sql.examples.sqltodataflow.Data
import portals.libraries.sql.examples.sqltodataflow.Types
import portals.libraries.sql.examples.sqltodataflow.Types.given
import portals.libraries.sql.internals.*
import portals.libraries.sql.sqlDSL.*
import portals.system.Systems

/** An example with a queryable Key-Value table using the sql library.
  *
  * Uses the TableWorkflow and Query task.
  *
  * @example
  *   {{{
  *  sbt "libraries/runMain portals.libraries.sql.examples.sqltodataflow.SQLToDataflow"
  *   }}}
  *
  * @see
  *   [[portals.libraries.sql.examples.sqltodataflow.Types]]
  * @see
  *   [[portals.libraries.sql.examples.sqltodataflow.SQLToRemoteDataflow]]
  */
object SQLToDataflow extends App:

  import org.slf4j.LoggerFactory
  import ch.qos.logback.classic.Level
  import ch.qos.logback.classic.Logger

  val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
  logger.setLevel(Level.INFO)

  /** Portals application which runs the queriable KV Table. */
  val tableApp = PortalsApp("SQLToDataflowTable"):

    /** A Table Workflow which serves SQL queries for the table of type KV. */
    val table = TableWorkflow[Types.KV]("KVTable", "k")

    /** Input queries for the Query task to execute. */
    val generator = Generators.fromIteratorOfIterators[String](
      Data.queryIterOfIter
    )

    /** Workflow which consumes the generated queries and runs query task. */
    val queryWorkflow = Workflows[String, String]("queryWorkflow")
      .source(generator.stream)
      /** A query task which connects to the `table`. */
      .query(table)
      .logger("Results: ")
      .sink()
      .freeze()

  /** Launch the application. */
  val system = Systems.test()
  system.launch(tableApp)
  system.stepUntilComplete()
  system.shutdown()
