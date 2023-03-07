package portals.examples

import scala.annotation.experimental
import scala.concurrent.Await

import portals.*
import portals.api.dsl.DSL
import portals.api.dsl.ExperimentalDSL
import portals.application.task.PerTaskState
import portals.system.Systems
import portals.util.Future

@experimental
@main def DynamicQuery(): Unit =
  import portals.api.dsl.DSL.*

  import portals.api.dsl.ExperimentalDSL.*

  case class Query()
  case class QueryReply(x: Int)

  val dynamicQueryApp = PortalsApp("DynamicQuery") {
    ////////////////////////////////////////////////////////////////////////////
    // Aggregator
    ////////////////////////////////////////////////////////////////////////////
    // Atom(1, 2, 3, 4), Atom(1, 2, 3, 4), ...
    val generator = Generators.fromListOfLists(List.fill(10)(List(1, 2, 3, 4)))

    val portal = Portal[Query, QueryReply]("portal")

    val aggregator = Workflows[Int, Nothing]("aggregator")
      .source(generator.stream)
      .replier[Nothing](portal) { x =>
        val sum = PerTaskState("sum", 0)
        sum.set(sum.get() + x) // aggregates sum
      } { case Query() =>
        reply(QueryReply(PerTaskState("sum", 0).get()))
      }
      .sink()
      .freeze()

    ////////////////////////////////////////////////////////////////////////////
    // Query
    ////////////////////////////////////////////////////////////////////////////
    val queryTrigger = Generators.fromListOfLists(List.fill(10)(List(0)))

    val queryWorkflow = Workflows[Int, Nothing]("queryWorkflow")
      .source(queryTrigger.stream)
      .asker[Nothing](portal) { x =>
        // query the aggregate
        val future: Future[QueryReply] = ask(portal)(Query())
        future.await {
          future.value.get match
            case QueryReply(x) =>
              // print the aggregate to log
              ctx.log.info(x.toString())
        }
      }
      .sink()
      .freeze()

  } // end dynamicQueryApp

  val system = Systems.interpreter()

  system.launch(dynamicQueryApp)

  // stepping should output only replied aggregates of whole numbers, i.e. divisible by 10.
  system.stepUntilComplete()

  system.shutdown()
