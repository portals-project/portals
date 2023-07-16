package portals.libraries.sql.internals

import java.util.concurrent.LinkedBlockingQueue

import scala.annotation.experimental
import scala.reflect.ClassTag

import org.apache.calcite.sql.`type`.SqlTypeName

import portals.api.builder.ApplicationBuilder
import portals.api.builder.FlowBuilder
import portals.api.dsl.DSL.*
import portals.api.dsl.DSL.Portal
import portals.application.task.PerKeyState
import portals.application.task.PerTaskState
import portals.application.AtomicPortalRef
import portals.libraries.sql.internals.calcite.*
import portals.libraries.sql.internals.CommitOp
import portals.libraries.sql.internals.DBSerializable
import portals.libraries.sql.internals.FirstPhaseResult
import portals.libraries.sql.internals.InsertOp
import portals.libraries.sql.internals.PreCommitOp
import portals.libraries.sql.internals.Result
import portals.libraries.sql.internals.RollbackOp
import portals.libraries.sql.internals.SQLQueryEvent
import portals.libraries.sql.internals.SelectOp
import portals.util.Future

type SQLPortal = AtomicPortalRef[SQLQueryEvent, Result]

case class TableInfo(
    tableName: String,
    primaryField: String,
    portal: SQLPortal,
    fieldNames: Array[String],
    fieldTypes: Array[SqlTypeName]
)

class PersistentLinkedBlockingQueue[T](qName: String) extends LinkedBlockingQueue[T] {
  val state = PerTaskState(qName, this)
}

class PersistentList[T](name: String) extends java.util.ArrayList[T] {
  val state = PerTaskState(name, this)
}

object QueryableWorkflow:
  import portals.api.dsl.ExperimentalDSL.*

  def createDataWfPortal(portalName: String)(using ab: ApplicationBuilder): SQLPortal =
    Portal[SQLQueryEvent, Result](
      portalName,
      qEvent =>
        qEvent match
          case PreCommitOp(tableName, key, txnId, op) =>
            key
          case SelectOp(tableName, key, txnId) =>
            key
          case InsertOp(tableName, data, key, txnId) =>
            key
          case RollbackOp(tableName, key, txnId) =>
            key
          case CommitOp(tableName, key, txnId) =>
            key
          case null => 0
    )

  val clsToSqlTypeMapping = Map[Class[_], SqlTypeName](
    classOf[Int] -> SqlTypeName.INTEGER,
    classOf[Integer] -> SqlTypeName.INTEGER,
    classOf[String] -> SqlTypeName.VARCHAR,
  )

  // Note: primary key is default to be the first field
  def createTable[T: ClassTag](
      tableName: String,
      primaryField: String,
      dBSerializable: DBSerializable[T],
      dataWfTransactional: Boolean = false
  )(using ab: ApplicationBuilder) = {
    // get all fields using reflection, then map to Sql type
    val clazz = implicitly[ClassTag[T]].runtimeClass
    val fields = clazz.getDeclaredFields
    val fieldNames = fields.map(_.getName)
    val fieldTypes = fields.map(f => clsToSqlTypeMapping(f.getType))

    val portal = createDataWfPortal(tableName)

    if dataWfTransactional then createDataWorkflowTxn(tableName, portal, dBSerializable)
    else createDataWorkflow(tableName, portal, dBSerializable)

    TableInfo(tableName, primaryField, portal, fieldNames, fieldTypes)
  }

  def createDataWorkflowTxn[T](
      tableName: String,
      portal: AtomicPortalRef[SQLQueryEvent, Result],
      dbSerializable: DBSerializable[T],
      defaultValue: Any = Array(0)
  )(using ab: ApplicationBuilder) = {
    Workflows[Nothing, Nothing](tableName + "Wf")
      .source(Generators.empty.stream)
      .replier[Nothing](portal) { _ =>
        ()
      } { q =>
        lazy val state = PerKeyState[Option[T]](tableName, None)
        lazy val unCommittedState = PerKeyState[Option[T]](tableName + "_uncommitted", None)
        lazy val _txn = PerKeyState[Int]("txn", -1)

        q match
          case PreCommitOp(tableName, key, txnId, op) =>
            if _txn.get() == -1 || _txn.get() == txnId then
              _txn.set(txnId)
              reply(Result(STATUS_OK, op))
            else
              reply(Result("error", List()))
          // first try to ready uncommitted state, if not initialized, then read committed state
          case SelectOp(tableName, key, txnId) =>
            if unCommittedState.get().isDefined then
              reply(Result(STATUS_OK, dbSerializable.toObjectArray(unCommittedState.get().get)))
            else
              val data = state.get()
              if (data.isDefined)
                reply(Result(STATUS_OK, dbSerializable.toObjectArray(data.get)))
              else
                reply(Result(STATUS_OK, Array(key.asInstanceOf[Object], 0.asInstanceOf[Object])))
          case InsertOp(tableName, data, key, txnId) =>
            unCommittedState.set(Some(dbSerializable.fromObjectArray(data)))
            reply(Result(STATUS_OK, Array[Object]()))
          case RollbackOp(tableName, key, txnId) =>
            unCommittedState.set(None)
            _txn.set(-1)
            reply(Result(STATUS_OK, Array[Object]()))
          case CommitOp(tableName, key, txnId) =>
            // println("commit txn " + txnId + " key " + key)
            if unCommittedState.get().isDefined then
              state.set(unCommittedState.get())
              unCommittedState.set(None)
            _txn.set(-1)
            reply(Result(STATUS_OK, Array[Object]()))
          case null => reply(Result("error", List()))
      }
      .sink()
      .freeze()
  }

  def createDataWorkflow[T](
      tableName: String,
      portal: AtomicPortalRef[SQLQueryEvent, Result],
      dbSerializable: DBSerializable[T]
  )(using ab: ApplicationBuilder) = {
    Workflows[Nothing, Nothing](tableName + "Wf")
      .source(Generators.empty.stream)
      .replier[Nothing](portal) { _ =>
        ()
      } { q =>
        lazy val state = PerKeyState[Option[T]](tableName, None)
        lazy val _txn = PerKeyState[Int]("txn", -1)

        q match
          case SelectOp(tableName, key, txnId) =>
            val data = state.get()
            _txn.set(-1)
            if (data.isDefined)
              reply(Result(STATUS_OK, dbSerializable.toObjectArray(data.get)))
            else
              reply(Result(STATUS_OK, null))
          case InsertOp(tableName, data, key, txnId) =>
            state.set(Some(dbSerializable.fromObjectArray(data)))
            reply(Result(STATUS_OK, Array[Object]()))
          case null => reply(Result("error", List()))
      }
      .sink()
      .freeze()
  }

extension [T, U](wb: FlowBuilder[T, U, String, String]) {

  def id(): FlowBuilder[T, U, String, String] = wb

  def querier(tableInfos: TableInfo*): FlowBuilder[T, U, String, String] = _querier(tableInfos: _*)(true)

  def _querier(tableInfos: TableInfo*)(printResult: Boolean): FlowBuilder[T, U, String, String] = {
    import scala.jdk.CollectionConverters.*
    import java.math.BigDecimal
    import portals.api.dsl.ExperimentalDSL.*

    /**
      * Portals                                           Calcite
      *   1. query arrives
      *                                                     2. start executing SQL
      *                                                     3. extract table count info from logical plan and reply
      *                                                        through tableOptCntCond.put
      *   4. get table number involved for this query
      *     through tableOptCntCond.take
      *                                                     5. call sub-queries for each table, inform when each table's
      *                                                        futures are ready through futureReadyCond.put
      *   6. acknolwedge futures for one table are ready
      *      through futureReadyCond.take
      *   7. if sub-queries for all tables are sent,
      *      call awaitAll, in the callback, notify 
      *      Calcite through awaitForFutureCond.put 
      *                                                     8. all tableScan nodes ready, continue non-leaf node execution,
      *                                                          notify Portals when execution completes
      *   9. read SQL query result
      */                                                      
    wb.asker[String](tableInfos.map(_.portal): _*) { sql =>
      // all kinds to blocking queues to synchronize between Calcite and Portals 
      val futureReadyCond = PersistentLinkedBlockingQueue[Integer]("futureReadyCond")
      val awaitForFutureCond = PersistentLinkedBlockingQueue[Integer]("awaitForFutureCond")
      val awaitForFinishCond = PersistentLinkedBlockingQueue[Integer]("awaitForFinishCond")
      val tableOptCntCond = PersistentLinkedBlockingQueue[Integer]("tableOptCntCond")
      // a container sent to Calcite to store the result of the query
      val result = PersistentList[Array[Object]]("result")
      // a container for all the Futures for sub-queries
      val futures = PersistentList[FutureWithResult]("futures")

      val calcite = new Calcite()
      calcite.printPlan = false

      // register to Calcite all the table metadata and lambdas about how to send sub-queries to these tables
      tableInfos.foreach(ti => {
        calcite.registerTable(ti.tableName, ti.fieldTypes.toList.asJava, ti.fieldNames.toList.asJava, 0)
        calcite
          .getTable(ti.tableName)
          .setInsertRow(data => {
            // Note: always assert that the first field is the primary key
            val future = ask(ti.portal)(InsertOp(ti.tableName, data.toList, data(0).asInstanceOf[Int]))
            new FutureWithResult(future, null)
          })
        calcite
          .getTable(ti.tableName)
          .setGetFutureByRowKeyFunc(key => {
            val future =
              ask(ti.portal)(SelectOp(ti.tableName, key.asInstanceOf[BigDecimal].toBigInteger.intValueExact()))
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
        ctx.emit(x)
      }

      for (i <- 1 to tableOptCnt) {
        // wait for one table's sub-queries to be called and their corresponding Futures ready
        futureReadyCond.take

        // Each tableScan node wait for one awaitForFutureCond message before 
        // considering itself to be ready (see awaitForFuturesCond.take in MPFTable.java)
        if i != tableOptCnt then awaitForFutureCond.put(1)
        else
          // All sub-queries for all tables are sent, now we call awaitAll.
          // In the callback when results are received, 
          // we need to extract the future's internal value as Object[] and put it
          // into the futureResult field.
          // (don't know how to do this in Java code, so I do it here)
          awaitAll[Result](futures.asScala.map(_.future.asInstanceOf[Future[Result]]).toList: _*) {
            // extract result from portals.util.Future to the futureResult field as a object array 
            futures.forEach(f => {
              val data = f.future.asInstanceOf[Future[Result]].value
              f.futureResult = f.future.asInstanceOf[Future[Result]].value.get.data.asInstanceOf[Array[Object]]
            })

            // All leaf tableScan/tableModify nodes are ready, 
            // send the awaitForFutureCond message for the last table to notify Calcite
            // so the SQL execution (for non-leaf nodes) can start
            awaitForFutureCond.put(1)

            // wait for the SQL execution to finish
            awaitForFinishCond.take()

            if printResult then
              println("====== Result for " + sql + " ======")
              result.forEach(row => println(java.util.Arrays.toString(row)))

            result.forEach(row => {
              emit(java.util.Arrays.toString(row))
            })
          }
      }
    }
  }
}

// Transactional version contains two asker operators corresponding to two phases of the transaction.
// Note this is not the desired protocol yet, since each query will be executed twice (one for precommit, 
// one for execution).

// For example, SELECT * WHERE key = 1 will first precommit and lock the row with key 1,
// and in the second phase, it will send sub-query to fetch the result.
// This process could be done in one round. 

// The first asker operator sends precommit sub-queries to all involved keys and check if this lock
//   aquisition is successful
// The second asker operator is responsible for sending rollback sub-queries to all the keys that are
//   already locked by this transaction if the first phase fails, or send real sub-queries to fetch the
//   query result.
// Commit query is handled separately, it will only be handled in the second asker.
val COMMIT_QUERY = "COMMIT"

extension [T, U](wb: FlowBuilder[T, U, TxnQuery, TxnQuery]) {
  def querierTransactional(tableInfos: TableInfo*): FlowBuilder[T, U, FirstPhaseResult, String] = {
    import scala.jdk.CollectionConverters.*
    import java.math.BigDecimal
    import portals.api.dsl.ExperimentalDSL.*

    val tableNameToPortal = tableInfos.map(ti => (ti.tableName, ti.portal)).toMap
    
    // TODO don't know how to initialize state for asker operator, 
    // `init` must output GenericTask[CU, CCU, Nothing, Nothing]
    // This is for storing precommitted keys
    val preCommittedOps = PerTaskState("preCommittedOps", java.util.HashMap[Integer, List[SQLQueryEvent]]())

    wb.asker[FirstPhaseResult](tableInfos.map(_.portal): _*) { txnQuery =>
      val futureReadyCond = PersistentLinkedBlockingQueue[Integer]("futureReadyCond")
      val awaitForFutureCond = PersistentLinkedBlockingQueue[Integer]("awaitForFutureCond")
      val awaitForFinishCond = PersistentLinkedBlockingQueue[Integer]("awaitForFinishCond")
      val tableOptCntCond = PersistentLinkedBlockingQueue[Integer]("tableOptCntCond")
      val result = PersistentList[Array[Object]]("result")
      val futures = PersistentList[FutureWithResult]("futures")

      val emit = { (x: FirstPhaseResult) =>
        ctx.emit(x)
      }

      val calcite = new Calcite()
      calcite.printPlan = false

      val txnId = txnQuery.txnId
      val sql = txnQuery.sql

      if sql.equals(COMMIT_QUERY) then
        emit(FirstPhaseResult(txnId, sql, true, List()))
      else
        tableInfos.foreach(ti => {
          calcite.registerTable(ti.tableName, ti.fieldTypes.toList.asJava, ti.fieldNames.toList.asJava, 0)
          // send precommit sub-queries to all involved keys
          calcite
            .getTable(ti.tableName)
            .setInsertRow(data => {
              // Note: assert primary key always be the first field
              val future = ask(ti.portal)(
                PreCommitOp(
                  ti.tableName,
                  data(0).asInstanceOf[Int],
                  txnId,
                  InsertOp(ti.tableName, data.toList, data(0).asInstanceOf[Int], txnId)
                )
              )
              new FutureWithResult(future, null)
            })
          calcite
            .getTable(ti.tableName)
            .setGetFutureByRowKeyFunc(key => {
              val intKey = key.asInstanceOf[BigDecimal].toBigInteger.intValueExact()
              val future = ask(ti.portal)(PreCommitOp(ti.tableName, intKey, txnId, SelectOp(ti.tableName, intKey, txnId)))
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

        val tableScanCnt = tableOptCntCond.take

        for (i <- 1 to tableScanCnt) {
          futureReadyCond.take

          // wait for the last one to awaitAll
          if i != tableScanCnt then awaitForFutureCond.put(1)
          else
            awaitAll[Result](futures.asScala.map(_.future.asInstanceOf[Future[Result]]).toList: _*) {
              val results: List[Result] = futures.asScala.map(_.future.asInstanceOf[Future[Result]].value.get).toList
              val succeedOps = results.filter(_.status == STATUS_OK).map(_.data.asInstanceOf[SQLQueryEvent])

              // TODO: made a partial commit example
              if succeedOps.size != futures.size() then {
                awaitForFutureCond.put(-1) // trigger execution failure
                emit(FirstPhaseResult(txnId, sql, false, succeedOps))
              } else
                emit(
                  FirstPhaseResult(txnId, sql, true, succeedOps, futures,
                    awaitForFutureCond, awaitForFinishCond, result
                  )
                )
            }
        }
    }
    .asker[String](tableInfos.map(_.portal): _*) { (preCommResult: FirstPhaseResult) =>
      val emit = { (x: String) =>
        ctx.emit(x)
      }

      // uncomment this to see the result of precommit for each query
      // println("====== Txn " + preCommResult.txnID
      //   + " Query " + preCommResult.sql 
      //   + " Phase 2 | success: " + preCommResult.success + " ======")

      val txnId = preCommResult.txnID

      if preCommResult.sql.equals(COMMIT_QUERY) then 
        var futures = List[Future[Result]]()
        preCommittedOps.get().getOrDefault(txnId, List()).foreach { op =>
          // println("send commit op txn:" + op.txnId + " key:" + op.key)
          futures = futures :+ ask(tableNameToPortal(op.tableName))(CommitOp(op.tableName, op.key, op.txnId))
        }
        // clear state after commit
        awaitAll[Result](futures: _*) {
          // println("clear state for txn " + txnId + " after commit")
          preCommittedOps.get().remove(txnId)
          emit("commit")
        }
      else
        if preCommResult.success then 
          // if precommit succeed, record all involved keys for later usage
          preCommittedOps.get().putIfAbsent(txnId, List[SQLQueryEvent]())
          preCommResult.succeedOps.foreach(op => {
            preCommittedOps.get().put(txnId, preCommittedOps.get().get(txnId) :+ op)
          })

          // send the actual sub-queries in the second phase 
          var futures = List[Future[Result]]()
          preCommResult.succeedOps.foreach { op =>
            futures = futures :+ ask(tableNameToPortal(op.tableName))(op)
          }
          // after results for all sub-queries are ready, 
          // send the last awaitForFutureCond message to Calcite so the query execution may continue
          awaitAll[Result](futures: _*) {
            for (i <- futures.indices) {
              preCommResult.preparedOps.get(i).futureResult = futures(i).value.get.data.asInstanceOf[Array[Object]]
            }
            preCommResult.awaitForFutureCond.put(1)
            preCommResult.awaitForFinishCond.take()

            preCommResult.result.forEach(row => {
              emit(java.util.Arrays.toString(row))
            })
          }
        // if first phase failed, rollback all successfully pre-committed sub-queries
        else
          var futures = List[Future[Result]]()
          preCommittedOps.get().getOrDefault(txnId, List()).foreach { op =>
            println("rollback txn " + op.txnId + " key " + op.key)
            futures = futures :+ ask(tableNameToPortal(op.tableName))(RollbackOp(op.tableName, op.key, op.txnId))
          }
          
          // clear state after rollback
          if futures.nonEmpty then
            awaitAll[Result](futures: _*) {
              preCommittedOps.get().remove(txnId)
              emit("precommit failed")
            }
          else
            emit("precommit failed")

    }
  }
}
