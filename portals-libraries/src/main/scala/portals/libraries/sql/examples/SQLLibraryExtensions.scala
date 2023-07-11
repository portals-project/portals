package portals.libraries.sql.examples

import java.math.BigDecimal
import java.util.concurrent.LinkedBlockingQueue

import scala.annotation.experimental
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

import org.apache.calcite.sql.`type`.SqlTypeName

import portals.api.builder.ApplicationBuilder
import portals.api.builder.FlowBuilder
import portals.api.dsl.DSL.*
import portals.api.dsl.DSL.Portal
import portals.api.dsl.ExperimentalDSL.*
import portals.application.task.*
import portals.application.task.PerKeyState
import portals.application.task.PerTaskState
import portals.application.AtomicPortalRef
import portals.libraries.sql
import portals.libraries.sql.*
import portals.libraries.sql.calcite.*
import portals.libraries.sql.CommitOp
import portals.libraries.sql.DBSerializable
import portals.libraries.sql.FirstPhaseResult
import portals.libraries.sql.InsertOp
import portals.libraries.sql.PreCommitOp
import portals.libraries.sql.Result
import portals.libraries.sql.RollbackOp
import portals.libraries.sql.SQLQueryEvent
import portals.libraries.sql.SelectOp
import portals.system.Systems
import portals.util.Future

/** DSL and extensions for the SQL Library to make it seem more like the
  * queryable library.
  *
  * **Warnings and Notes:** names are tricky, it is best to avoid using the
  * following reserved words:
  *   - Table
  *   - Value
  */
object SQLLibraryExtensions:
  import scala.reflect.ClassTag

  import portals.api.builder.*

  private inline val TRANSACTIONAL = false

  def Table[T: DBSerializable: ClassTag](
      tableName: String,
      primaryField: String,
  ): ApplicationBuilder ?=> TableInfo =
    QueryableWorkflow
      .createTable(
        tableName,
        primaryField,
        summon[DBSerializable[T]],
        TRANSACTIONAL,
      )

  case class QueryPortalInfo(
      portal: AtomicPortalRef[String, String],
  )

  def QueryPortal(
      name: String,
      table: TableInfo,
  ): ApplicationBuilder ?=> QueryPortalInfo =
    val portal = Portal[String, String](name)
    Workflows[Nothing, Nothing]()
      .source(Generators.empty[Nothing].stream)
      .askerreplier[Nothing, Any, Any](table.portal.asInstanceOf)(portal.asInstanceOf) { x => ??? } { //
        x =>
          queryAsk(table)(x.asInstanceOf[String])
      }
      .sink()
      .freeze()
    val qpi = QueryPortalInfo(
      portal
    )
    qpi

  extension [T, U](wb: FlowBuilder[T, U, String, String]) {
    def query(tables: TableInfo*): FlowBuilder[T, U, String, String] =
      if TRANSACTIONAL then //
        wb.querierTransactional(tables: _*).asInstanceOf[FlowBuilder[T, U, String, String]]
      else //
        wb.querier(tables: _*)
  }

  private def queryAsk(
      tableInfos: TableInfo*
  ): AskerReplierTaskContext[Nothing, Nothing, Any, Any] ?=> String => Unit = { sql =>
    val futureReadyCond = PersistentLinkedBlockingQueue[Integer]("futureReadyCond")
    val awaitForFutureCond = PersistentLinkedBlockingQueue[Integer]("awaitForFutureCond")
    val awaitForFinishCond = PersistentLinkedBlockingQueue[Integer]("awaitForFinishCond")
    val tableOptCntCond = PersistentLinkedBlockingQueue[Integer]("tableOptCntCond")
    val result = PersistentList[Array[Object]]("result")
    val futures = PersistentList[FutureWithResult]("futures")
    val portalFutures = PersistentList[Future[Result]]("portalFutures")

    val calcite = new Calcite()
    calcite.printPlan = false

    // insert
    tableInfos.foreach(ti => {
      calcite.registerTable(ti.tableName, ti.fieldTypes.toList.asJava, ti.fieldNames.toList.asJava, 0)
      calcite
        .getTable(ti.tableName)
        .setInsertRow(data => {
          // TODO: assert pk always Int
          val future = ask(ti.portal.asInstanceOf)(InsertOp(ti.tableName, data.toList, data(0).asInstanceOf[Int]))
          portalFutures.add(future.asInstanceOf)
          new FutureWithResult(future, null)
        })
      calcite
        .getTable(ti.tableName)
        .setGetFutureByRowKeyFunc(key => {
          val future =
            ask(ti.portal.asInstanceOf)(
              SelectOp(ti.tableName, key.asInstanceOf[BigDecimal].toBigInteger.intValueExact())
            )
          portalFutures.add(future.asInstanceOf)
          new FutureWithResult(future, null)
        })
    })

    calcite.executeSQL(
      sql,
      futureReadyCond,
      awaitForFutureCond,
      awaitForFinishCond,
      tableOptCntCond,
      futures,
      result
    )

    val tableOptCnt = tableOptCntCond.take

    val emit = { (x: String) =>
      // ctx.emit(x)
      // reply(x)
      ???
    }

    for (i <- 1 to tableOptCnt) {
//        println("try future ready consume")
      futureReadyCond.take
//        println("future ready consume done")

      // wait for the last one to awaitAll
      if i != tableOptCnt then awaitForFutureCond.put(1)
      else
        awaitAll[Result](portalFutures.asScala.toList: _*) {
          futures.forEach(f => {
            val data = f.future.asInstanceOf[Future[Result]].value
            f.futureResult = f.future.asInstanceOf[Future[Result]].value.get.data.asInstanceOf[Array[Object]]
          })

          awaitForFutureCond.put(1) // allow SQL execution to start

          awaitForFinishCond.take() // wait for SQL execution to finish

          // if printResult then
          //   println("====== Result for " + sql + " ======")
          //   result.forEach(row => println(java.util.Arrays.toString(row)))

          // result.forEach(row => {
          //   emit(java.util.Arrays.toString(row))
          // })
          // emit(result.toArray().map(row => java.util.Arrays.toString(row)).mkString("\n"))
          reply(result.asScala.map(row => java.util.Arrays.toString(row)).mkString("\n"))

        }(using ctx.asInstanceOf)
    }
  }
