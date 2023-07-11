package portals.libraries.sql

import java.math.BigDecimal
import java.util.concurrent.LinkedBlockingQueue

import scala.annotation.experimental
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

import org.apache.calcite.sql.`type`.SqlTypeName

import portals.api.builder.*
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
import portals.libraries.sql.internals.*
import portals.libraries.sql.internals.calcite.*
import portals.libraries.sql.internals.QueryableWorkflow.clsToSqlTypeMapping
import portals.libraries.sql.internals.QueryableWorkflow.createDataWfPortal
import portals.system.Systems
import portals.util.Future

/** DSL extensions for the SQL Library.
  *
  * Info: This is a temporary DSL extension to the SQL library in order to make
  * it more alike the queryable library.
  *
  * Warning: Names are tricky, it is best to avoid using the following reserved
  * words with the SQL DSL, as they conflict with the Calcite SQL keywords:
  *   - Table
  *   - Value
  */
object sqlDSL:
  //////////////////////////////////////////////////////////////////////////////
  // CONSTANTS
  //////////////////////////////////////////////////////////////////////////////

  private inline val TRANSACTIONAL = false

  /** Transactional is not yet supported. For this we need to port the
    * transactional query asker and transactional query replier into this file
    * also. Additionally, we would need to make the choice between the two at
    * the point when the asker/replier is created (which is not currently
    * supported.) Ask JS for more details.
    */
  if TRANSACTIONAL == true then throw Exception("Transactional SQL is not yet supported.")

  //////////////////////////////////////////////////////////////////////////////
  // TYPES
  //////////////////////////////////////////////////////////////////////////////

  private[this] object Types:
    type Table = TableInfo

    extension (t: Table) {

      /** Get a `ref`erence to this table. */
      def ref: TableRef =
        TableRef(t.tableName, t.primaryField, t.portal, t.fieldNames, t.fieldTypes)
    }

    case class TableRef(
        tableName: String,
        primaryField: String,
        portal: SQLPortal,
        fieldNames: Array[String],
        fieldTypes: Array[SqlTypeName]
    )

    extension (t: TableRef) {

      /** Internal API. DO NOT USE! This is a hack to make it work for now. */
      def unref: TableInfo = TableInfo(t.tableName, t.primaryField, t.portal, t.fieldNames, t.fieldTypes)
    }
  export Types.*

  //////////////////////////////////////////////////////////////////////////////
  // TABLE
  //////////////////////////////////////////////////////////////////////////////

  def TableWorkflow[T: DBSerializable: ClassTag](
      tableName: String,
      primaryField: String,
  ): ApplicationBuilder ?=> TableRef =
    QueryableWorkflow
      .createTable(
        tableName,
        primaryField,
        summon[DBSerializable[T]],
        TRANSACTIONAL,
      )
      .ref

  def Table[T: DBSerializable: ClassTag](
      tableName: String,
      primaryField: String,
  ): ApplicationBuilder ?=> Table =
    val clazz = implicitly[ClassTag[T]].runtimeClass
    val fields = clazz.getDeclaredFields
    val fieldNames = fields.map(_.getName)
    val fieldTypes = fields.map(f => clsToSqlTypeMapping(f.getType))
    val portal = createDataWfPortal(tableName)
    TableInfo(tableName, primaryField, portal, fieldNames, fieldTypes)

  extension [T, U, CT](wb: FlowBuilder[T, U, CT, Nothing]) {
    def tablex[X: DBSerializable: ClassTag](tables: Table*): FlowBuilder[T, U, Nothing, Nothing] =
      wb.task(
        Utils.tableReplier[X](tables.head).asInstanceOf
      )
  }

  ////////////////////////////////////////////////////////////////////////////
  // QUERY
  ////////////////////////////////////////////////////////////////////////////

  case class QueryPortalInfo(
      portal: AtomicPortalRef[String, String],
  )

  def QueryPortal(
      name: String,
      tableref: TableRef,
  ): ApplicationBuilder ?=> QueryPortalInfo =
    val portal = Portal[String, String](name)
    Workflows[Nothing, Nothing]()
      .source(Generators.empty[Nothing].stream)
      .askerreplier[Nothing, Any, Any](tableref.portal.asInstanceOf)(portal.asInstanceOf) { x => ??? } { //
        x =>
          Utils.queryAsk(tableref.unref)(x.asInstanceOf[String])
      }
      .sink()
      .freeze()
    val qpi = QueryPortalInfo(
      portal
    )
    qpi

  extension [T, U](wb: FlowBuilder[T, U, String, String]) {
    def query(tables: TableRef*): FlowBuilder[T, U, String, String] =
      if TRANSACTIONAL then //
        wb.querierTransactional(tables.map(_.unref): _*).asInstanceOf[FlowBuilder[T, U, String, String]]
      else //
        wb.querier(tables.map(_.unref): _*)
  }

  //////////////////////////////////////////////////////////////////////////////
  // UTIL
  //////////////////////////////////////////////////////////////////////////////

  private object Utils:
    def queryAsk(
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
    end queryAsk

    def tableReplier[T: DBSerializable](tableInfo: TableInfo): ReplierTask[Any, Any, Any, Any] =
      TaskBuilder
        .replier[Any, Any, Any, Any](tableInfo.portal.asInstanceOf) { _ => ??? } { //
          q =>
            lazy val state = PerKeyState[Option[Any]](tableInfo.tableName, None)
            lazy val _txn = PerKeyState[Int]("txn", -1)

            q match
              case PreCommitOp(_, key, txnId, op) =>
                if _txn.get() == -1 || _txn.get() == txnId then
                  _txn.set(txnId)
                  reply(Result(STATUS_OK, op))
                else reply(Result("error", List()))
              case SelectOp(_, key, txnId) =>
                val data = state.get()
                _txn.set(-1)
                if (data.isDefined)
                  reply(Result(STATUS_OK, summon[DBSerializable[T]].toObjectArray(data.get.asInstanceOf)))
                else
                  reply(Result(STATUS_OK, null))
              case InsertOp(_, data, key, txnId) =>
                state.set(Some(summon[DBSerializable[T]].fromObjectArray(data)))
                reply(Result(STATUS_OK, Array[Object]()))
              case RollbackOp(_, key, txnId) =>
                println("rollback txn " + txnId + " key " + key)
                _txn.set(-1)
                reply(Result(STATUS_OK, Array[Object]()))
              case null => reply(Result("error", List()))

        }
        .asInstanceOf
