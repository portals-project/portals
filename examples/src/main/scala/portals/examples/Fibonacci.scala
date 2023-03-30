package portals.examples

import scala.annotation.experimental

import portals.api.builder.ApplicationBuilder
import portals.application.Application
import portals.system.Systems
import portals.util.Futures

/** Fibonacci
  *
  * This example computes Fibonacci using recursive portals query
  */

case class FibQuery(n: Int)

case class FibResp(resp: Int)

@experimental
object Fibonacci:

  import portals.api.dsl.DSL.*
  import portals.api.dsl.ExperimentalDSL.*

  val app: Application = {
    val fibApp = PortalsApp("Fibonacci") {
      val internalPortal = Portal[FibQuery, FibResp]("internalPortal")
      val portal = Portal[FibQuery, FibResp]("portal")

      val generator = Generators.empty.stream

      val fibWorkflow = Workflows[Nothing, Nothing]("fibWf")
        .source(generator)
        .askerreplier[Nothing, FibQuery, FibResp](internalPortal)(portal)(_ => ()) { case FibQuery(n) =>
          if n == 1 || n == 2 then reply(FibResp(1))
          else
            val future1 = ask(internalPortal)(FibQuery(n - 1))
            val future2 = ask(internalPortal)(FibQuery(n - 2))

            Futures.awaitAll(future1, future2) { resp =>
              reply(FibResp(resp.map(_.resp).sum))
            }
        }
        .askerreplier[Nothing, FibQuery, FibResp](internalPortal)(internalPortal)(_ => ()) { case FibQuery(n) =>
          if n == 1 || n == 2 then reply(FibResp(1))
          else
            val future1 = ask(internalPortal)(FibQuery(n - 1))
            val future2 = ask(internalPortal)(FibQuery(n - 2))
            Futures.awaitAll(future1, future2) { resp =>
              reply(FibResp(resp.map(_.resp).sum))
            }
        }
        .sink()
        .freeze()

      val queryGenerator = Generators.fromList(List(FibQuery(10))).stream
      val querierWorkflow = Workflows[FibQuery, Nothing]("fibQueryWf")
        .source(queryGenerator)
        .asker[Nothing](portal) { query =>
          val future = ask(portal)(query)
          await(future) {
            ctx.log.info(future.value.get.resp.toString)
          }
        }
        .sink()
        .freeze()
    }

    fibApp
  }

@experimental
@main def FibonacciMain(): Unit =
  val application = Fibonacci.app
  val system = Systems.interpreter()
  system.launch(application)
  system.stepUntilComplete()
  system.shutdown()
