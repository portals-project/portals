package portals.sql

import org.apache.calcite.sql.`type`.SqlTypeName
import portals.api.dsl.DSL.Portal
import portals.api.builder.{ApplicationBuilder, FlowBuilder}
import portals.application.AtomicPortalRef
import portals.api.dsl.DSL.*
import portals.application.task.PerKeyState
import portals.util.Future

import java.util.concurrent.LinkedBlockingQueue
import scala.annotation.experimental
import scala.reflect.ClassTag

type SQLPortal = AtomicPortalRef[SQLQueryEvent, Result]

case class TableInfo(
                      tableName: String,
                      primaryField: String,
                      portal: SQLPortal,
                      fieldNames: Array[String],
                      fieldTypes: Array[SqlTypeName]
                    )

@experimental
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
          case null => 0
    )


  val clsToSqlTypeMapping = Map[Class[_], SqlTypeName](
    classOf[Int] -> SqlTypeName.INTEGER,
    classOf[Integer] -> SqlTypeName.INTEGER,
    classOf[String] -> SqlTypeName.VARCHAR,
  )

  // TODO: default primary key to be the first field
  def createTable[T: ClassTag](
                      tableName: String,
                      primaryField: String,
                      dBSerializable: DBSerializable[T]
                    )(using ab: ApplicationBuilder) = {
    // get all fields using reflection, then map to Sql type
    val clazz = implicitly[ClassTag[T]].runtimeClass
    val fields = clazz.getDeclaredFields
    val fieldNames = fields.map(_.getName)
    val fieldTypes = fields.map(f => clsToSqlTypeMapping(f.getType))

    val portal = createDataWfPortal(tableName)
    createDataWorkflow(tableName, portal, dBSerializable)

    TableInfo(tableName, primaryField, portal, fieldNames, fieldTypes)
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
          case PreCommitOp(tableName, key, txnId, op) =>
            println("precommit txn " + txnId + " key " + key)
            if _txn.get() == -1 || _txn.get() == txnId then
              _txn.set(txnId)
              reply(Result(STATUS_OK, op))
            else reply(Result("error", List()))
          case SelectOp(tableName, key, txnId) =>
            val data = state.get()
            if (data.isDefined)
              println("select " + key + " " + data.get)
              reply(Result(STATUS_OK, dbSerializable.toObjectArray(data.get)))
            else
              reply(Result(STATUS_OK, null))
          case InsertOp(tableName, data, key, txnId) =>
            state.set(Some(dbSerializable.fromObjectArray(data)))
            println("inserted " + state.get().get)
            reply(Result(STATUS_OK, Array[Object]()))
          case RollbackOp(tableName, key, txnId) =>
            println("rollback txn " + txnId + " key " + key)
            _txn.set(-1)
            reply(Result(STATUS_OK, Array[Object]()))
          case null => reply(Result("error", List()))
      }
      .sink()
      .freeze()
  }

extension [T, U](wb: FlowBuilder[T, U, String, String]) {

  def id() : FlowBuilder[T, U, String, String] = wb
  @experimental
  def querier(tableInfos: TableInfo*) : FlowBuilder[T, U, String, String] = {
    import scala.jdk.CollectionConverters.*
    import java.math.BigDecimal
    import portals.api.dsl.ExperimentalDSL.*
    
    wb.asker[String](tableInfos.map(_.portal): _*) { sql =>
      val futureReadyCond = new LinkedBlockingQueue[Integer]()
      val awaitForFutureCond = new LinkedBlockingQueue[Integer]()
      val awaitForFinishCond = new LinkedBlockingQueue[Integer]()
      val tableScanCntCond = new LinkedBlockingQueue[Integer]()
      val result = new java.util.ArrayList[Array[Object]]()
      val futures = new java.util.ArrayList[FutureWithResult]()
      var portalFutures = List[Future[Result]]()

      val calcite = new Calcite()
      calcite.printPlan = false

      tableInfos.foreach(ti => {
        calcite.registerTable(ti.tableName, ti.fieldTypes.toList.asJava, ti.fieldNames.toList.asJava, 0)
        calcite.getTable(ti.tableName).setInsertRow(data => {
          // TODO: assert pk always Int
          val future = ask(ti.portal)(InsertOp(ti.tableName, data.toList, data(0).asInstanceOf[Int]))
          portalFutures = portalFutures :+ future
          new FutureWithResult(future, null)
        })
        calcite.getTable(ti.tableName).setGetFutureByRowKeyFunc(key => {
          val future = ask(ti.portal)(SelectOp(ti.tableName, key.asInstanceOf[BigDecimal].toBigInteger.intValueExact()))
          portalFutures = portalFutures :+ future
          new FutureWithResult(future, null)
        })
      })

      calcite.executeSQL(
        sql,
        futureReadyCond,
        awaitForFutureCond,
        awaitForFinishCond,
        tableScanCntCond,
        futures,
        result
      )

      val tableScanCnt = tableScanCntCond.take

      val emit = { (x: String) =>
        ctx.emit(x)
      }

      for (i <- 1 to tableScanCnt) {
        println("try future ready consume")
        futureReadyCond.take
        println("future ready consume done")

        // wait for the last one to awaitAll
        if i != tableScanCnt then awaitForFutureCond.put(1)
        else
          awaitAll[Result](portalFutures: _*) {
            futures.forEach(f => {
              val data = f.future.asInstanceOf[Future[Result]].value
              f.futureResult = f.future.asInstanceOf[Future[Result]].value.get.data.asInstanceOf[Array[Object]]
            })

            awaitForFutureCond.put(1) // allow SQL execution to start

            awaitForFinishCond.take() // wait for SQL execution to finish

            println("====== Result for " + sql + " ======")
            result.forEach(row => println(java.util.Arrays.toString(row)))

            result.forEach(row => {
              emit(java.util.Arrays.toString(row))
            })
          }
      }
    }
  }

  @experimental
  def querierTransactional(tableInfos: TableInfo*) : FlowBuilder[T, U, FirstPhaseResult, String] = {
    import scala.jdk.CollectionConverters.*
    import java.math.BigDecimal
    import portals.api.dsl.ExperimentalDSL.*

    val tableNameToPortal = tableInfos.map(ti => (ti.tableName, ti.portal)).toMap
    val rndTxnIDGenerator = scala.util.Random()
    rndTxnIDGenerator.setSeed(514)

    wb.asker[FirstPhaseResult](tableInfos.map(_.portal): _*) { sql =>
      val futureReadyCond = new LinkedBlockingQueue[Integer]()
      val awaitForFutureCond = new LinkedBlockingQueue[Integer]()
      val awaitForFinishCond = new LinkedBlockingQueue[Integer]()
      val tableScanCntCond = new LinkedBlockingQueue[Integer]()
      val result = new java.util.ArrayList[Array[Object]]()
      val futures = new java.util.ArrayList[FutureWithResult]()

      var portalFutures = List[Future[Result]]()

      val calcite = new Calcite()
      calcite.printPlan = false

      val txnId = rndTxnIDGenerator.nextInt(1000000)

      tableInfos.foreach(ti => {
        calcite.registerTable(ti.tableName, ti.fieldTypes.toList.asJava, ti.fieldNames.toList.asJava, 0)
        calcite.getTable(ti.tableName).setInsertRow(data => {
          // TODO: assert pk always Int
          val future = ask(ti.portal)(
            PreCommitOp(
              ti.tableName,
              data(0).asInstanceOf[Int],
              txnId,
              InsertOp(ti.tableName, data.toList, data(0).asInstanceOf[Int], txnId)
            )
          )
          portalFutures = portalFutures :+ future
          new FutureWithResult(future, null)
        })
        calcite.getTable(ti.tableName).setGetFutureByRowKeyFunc(key => {
          val intKey = key.asInstanceOf[BigDecimal].toBigInteger.intValueExact()
          val future = ask(ti.portal)(PreCommitOp(ti.tableName, intKey, txnId, SelectOp(ti.tableName, intKey, txnId)))
          portalFutures = portalFutures :+ future
          new FutureWithResult(future, null)
        })
      })

      calcite.executeSQL(
        sql,
        futureReadyCond,
        awaitForFutureCond,
        awaitForFinishCond,
        tableScanCntCond,
        futures,
        result
      )

      val tableScanCnt = tableScanCntCond.take
      println("tableScanCnt: " + tableScanCnt)

      val emit = { (x: FirstPhaseResult) =>
        ctx.emit(x)
      }

      for (i <- 1 to tableScanCnt) {
        futureReadyCond.take

        // wait for the last one to awaitAll
        if i != tableScanCnt then awaitForFutureCond.put(1)
        else
          awaitAll[Result](portalFutures: _*) {
            val results: List[Result] = futures.asScala.map(_.future.asInstanceOf[Future[Result]].value.get).toList
            val succeedOps = results.filter(_.status == STATUS_OK).map(_.data.asInstanceOf[SQLQueryEvent])

            // TODO: made a partial commit example
            if succeedOps.size != futures.size() then {
              awaitForFutureCond.put(-1) // trigger execution failure
              emit(FirstPhaseResult(txnId, false, succeedOps))
            } else
              emit(
                FirstPhaseResult(txnId, true, succeedOps, futures, awaitForFutureCond, awaitForFinishCond, result)
              )
          }
      }
    }
    .asker[String](tableInfos.map(_.portal): _*) { (preCommResult: FirstPhaseResult) =>
      val emit = { (x: String) =>
        ctx.emit(x)
      }

      if preCommResult.success then {
        println("txn " + preCommResult.txnID + " precommit succeed")
        var futures = List[Future[Result]]()
        preCommResult.succeedOps.foreach { op =>
          futures = futures :+ ask(tableNameToPortal(op.tableName))(op)
        }
        awaitAll[Result](futures: _*) {
          for (i <- futures.indices) {
            preCommResult.preparedOps.get(i).futureResult = futures(i).value.get.data.asInstanceOf[Array[Object]]
          }
          preCommResult.awaitForFutureCond.put(1)
          preCommResult.awaitForFinishCond.take()

          preCommResult.result.forEach(row => {
            //                emit(util.Arrays.toString(row))
            println(java.util.Arrays.toString(row))
            emit(java.util.Arrays.toString(row))
          })
        }
      } else {
        println("txn " + preCommResult.txnID + " precommit failed")
        var futures = List[Future[Result]]()
        preCommResult.succeedOps.foreach { op =>
          futures = futures :+ ask(tableNameToPortal(op.tableName))(RollbackOp(op.tableName, op.key, op.txnId))
        }
        awaitAll[Result](futures: _*) {
          println("rollback done")
          emit("rollback")
        }
      }
    }
  }
}
