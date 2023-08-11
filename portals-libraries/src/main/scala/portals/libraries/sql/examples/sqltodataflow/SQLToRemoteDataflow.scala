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
  * Uses the Table task and the Query Portal, and connects from a remote app to
  * the Query Portal.
  *
  * @example
  *   {{{
  *  sbt "libraries/runMain portals.libraries.sql.examples.sqltodataflow.SQLToRemoteDataflow"
  *   }}}
  *
  * @see
  *   [[portals.libraries.sql.examples.sqltodataflow.Types]]
  * @see
  *   [[portals.libraries.sql.examples.sqltodataflow.SQLToDataflow]]
  */
object SQLToRemoteDataflow extends App:
  import sqlDSL.*

  /** Portals application which runs the queriable KV Table. */
  val tableApp = PortalsApp("SQLToDataflowTable"):

    val table = Table[Types.KV]("KVTable", "k")

    val tableWorkflow = Workflows[Nothing, Nothing]()
      .source(Generators.empty[Nothing].stream)
      .table[Types.KV](table)
      .sink()
      .freeze()

    val queryPortal = QueryPortal("queryPortal", table.ref)

  /** Remote Portals applications which queries the Query Portal. */
  val remoteApp = PortalsApp("SQLToDataflowRemote"):

    /** Get a reference to the Query Portal from the Registry. */
    val queryPortal = Registry.portals.get[String, String]("/SQLToDataflowTable/portals/queryPortal")

    /** Input queries for the Query task to execute. */
    val generator = Generators.generator[String](Data.queriesGenerator)

    /** Workflow which sends the consumed SQL requests to the query portal. */
    val queryWorkflow = Workflows[String, String]("queryWorkflow")
      .source(generator.stream)
      .logger("Query:  ")
      .asker(queryPortal) { x =>
        // ask the query portal to execute the query
        val f = ask(queryPortal)(x)
        await(f) {
          // emit the result of the query
          emit(f.value.get)
        }
      }
      .logger("Results: ")
      .sink()
      .freeze()

  /** Launch the applications. */
  val system = Systems.test()
  system.launch(tableApp)
  system.launch(remoteApp)
  system.stepFor(10_000)
  system.shutdown()

  // Force quit for IDE, as otherwise other Threads might keep the program alive
  System.exit(0)
