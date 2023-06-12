package portals.examples.experimental

import scala.annotation.experimental

import portals.api.builder.ApplicationBuilder
import portals.api.builder.TaskBuilder
import portals.api.dsl.DSL.Generators
import portals.api.dsl.DSL.Portal
import portals.api.dsl.DSL.PortalsApp
import portals.api.dsl.DSL.Workflows
import portals.application.task.AskerTask
import portals.application.task.PerTaskState
import portals.application.task.ReplierTask
import portals.application.task.StatefulTaskContext
import portals.application.Application
import portals.application.AtomicPortalRef
import portals.runtime.manager.PortalStub
import portals.runtime.manager.SubmittableApplication
import portals.util.Future

class DynamicWorkflowSubmission {}

// =======================================
// =========== Util Classes ==============
// =======================================

case class Query(sql: String, requestId: String = "", key: String = "")

case class QueryResult(message: String, requestId: String = "", rows: List[Tuple] = List())

def keyFrom(query: Query): Long = query.key.hashCode()

@experimental
object WorkflowSubmissionUtils:

  import portals.api.dsl.DSL.*
  import portals.api.dsl.ExperimentalDSL.*

  def dummyAskingWorkflow(portal: AtomicPortalRef[_, _])(using ab: ApplicationBuilder) =
    Workflows[Nothing, Nothing]("dummyAskingWorkflow")
      .source(Generators.empty.stream)
      .asker[Nothing](portal) { * => () }
      .sink()
      .freeze()

  def dummyReplierWorkflow(portal: AtomicPortalRef[_, _])(using ab: ApplicationBuilder) =
    Workflows[Nothing, Nothing]("dummyReplierWorkflow")
      .source(Generators.empty.stream)
      .replier[Nothing](portal) { * => () } { * => () }
      .sink()
      .freeze()

// =======================================
// =========== Application ===============
// =======================================

@experimental
class BankAccountApplication extends SubmittableApplication {

  import portals.api.dsl.DSL.*
  import portals.api.dsl.ExperimentalDSL.*

  //  override def application: Application = Queryable.genBankAccountAppWithNoQuerier
  override def application: Application =
    PortalsApp("bankApp") {

      val bankAccountPortal = Portal[Query, QueryResult]("bankAccountPortal", keyFrom)

      val generator = Generators.fromList(List[String]("balabala"))
      Workflows[String, Nothing]("bankAccount-")
        .source(generator.stream)
        .task[Nothing](accountTask(bankAccountPortal))
        .withName("bankAccountTask")
        .withName("bankAccountReplier")
        .sink()
        .freeze()

      // NOTE: not used, for passing the wellformedness check only
      WorkflowSubmissionUtils.dummyAskingWorkflow(bankAccountPortal)
    }

  val replierState: StatefulTaskContext ?=> PerTaskState[Int] = PerTaskState("bankAccountState", 1)

  def accountTask(portals: AtomicPortalRef[Query, QueryResult]) =
    TaskBuilder.replier[String, Nothing, Query, QueryResult](portals)(e => ctx.log.info(s"received regular event $e")) {
      query =>
        ctx.log.info(s"received query: ${query.sql} [${query.requestId}]")
        replierState.set(replierState.get() + 1)
        reply(QueryResult(replierState.get().toString, query.requestId))
    }
}

@experimental
class QueryApplication extends SubmittableApplication {

  import portals.api.dsl.DSL.*
  import portals.api.dsl.ExperimentalDSL.*

  override def application: Application =
    import portals.api.dsl.ExperimentalDSL.*

    PortalsApp("queryApp") {
      ////////////////////////////////////////////////////////////////////////////
      // Query Origin
      ////////////////////////////////////////////////////////////////////////////
      val bankAccountPortal = Portal[Query, QueryResult]("bankAccountPortal", keyFrom)
      registerPortalStub(bankAccountPortal, "bankApp")

      val queries = Generators.fromList[Query](List(Query("xxx"), Query("yyy")))

      Workflows[Query, Nothing]("queryWorkflow")
        .source(queries.stream)
        .task(queryTask(bankAccountPortal))
        .withName("baQuerier")
        .withName("querier")
        .sink()
        .freeze()

      // NOTE: not used, for passing the wellformedness check only
      WorkflowSubmissionUtils.dummyReplierWorkflow(bankAccountPortal)
    }

  def queryTask(portals: AtomicPortalRef[Query, QueryResult]) =
    TaskBuilder.asker[Query, Nothing, Query, QueryResult](portals) { query =>
      ctx.log.info(s"rcvd query: ${query.sql} [${query.requestId}]")
      val future: Future[QueryResult] = ask(portals)(query)
      future.await {
        ctx.log.info(s"got reply: ${future.value.get.message} [${future.value.get.requestId}]")
      }
    }
}
