package portals.libraries.sql.examples.sqltodataflow

import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.libraries.sql.*
import portals.libraries.sql.examples.sqltodataflow.Data.*
import portals.libraries.sql.examples.sqltodataflow.Types
import portals.libraries.sql.examples.sqltodataflow.Types.given
import portals.libraries.sql.internals.*
import portals.libraries.sql.sqlDSL.*
import portals.system.Systems

/** An example with a queryable Key-Value table using the sql library.
  *
  * Two query workflow send queries to the same table.
  *
  * The query is transactional, but not interactive, it may happen that one
  * transaction fails, but still proceed to commit. Such behavior is undefined
  * (and should not be considered).
  *
  * @example
  *   {{{
  *  sbt "libraries/runMain portals.libraries.sql.examples.sqltodataflow.SQLToDataflowTxn"
  *   }}}
  *
  * @see
  *   [[portals.libraries.sql.examples.sqltodataflow.Types]]
  * @see
  *   [[portals.libraries.sql.examples.sqltodataflow.SQLToRemoteDataflow]]
  */
object SQLToDataflowTxn extends App:
  /** Portals application which runs the queriable KV Table. */
  val tableApp = PortalsApp("SQLToDataflowTable"):

    /** A Table Workflow which serves SQL queries for the table of type KV. */
    val table = TableWorkflow[Types.KV]("KVTable", "k", true)

    /** Transactional input queries for the Query task to execute. */
    val generator1 = Generators.generator[TxnQuery](Data.transactionalQueriesGenerator)

    /** More input queries for another query task. */
    val generator2 = Generators.generator[TxnQuery](Data.transactionalQueriesGenerator)

    /** Workflow which consumes the generated queries and runs query task. */
    val queryWorkflow1 = Workflows[TxnQuery, String]("queryWorkflow1")
      .source(generator1.stream)
      /** A query task which connects to the `table`. */
      .logger("Query  1: ")
      .queryTxn(table)
      .logger("Result 1: ")
      .sink()
      .freeze()

    /** Workflow which consumes the generated queries and runs query task. */
    val queryWorkflow2 = Workflows[TxnQuery, String]("queryWorkflow2")
      .source(generator2.stream)
      /** Another query task which connects to the `table`. */
      .logger("Query  2: ")
      .queryTxn(table)
      .logger("Result 2: ")
      .sink()
      .freeze()

  /** Launch the application. */
  val system = Systems.test()
  system.launch(tableApp)
  system.stepFor(10_000)
  system.shutdown()

  // force quit for IDE, as otherwise other Threads might keep the program alive
  System.exit(0)
