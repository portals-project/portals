package portals.sql.example

import java.util.concurrent.LinkedBlockingQueue

import scala.annotation.experimental
import scala.reflect.ClassTag

import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.application.task.PerKeyState
import portals.application.AtomicGeneratorRef
import portals.sql.DBSerializable
import portals.sql.QueryableWorkflow
import portals.system.Systems
import portals.util.Future

/** Simulate concurrent queries, in this case insert on key 0 will block select
  * on key (1, 0), precommit on key 1 will be rolled back
  */
@experimental
object TransactionalConflictProbabilistic extends App:

  import scala.jdk.CollectionConverters.*

  import org.slf4j.LoggerFactory

  // TODO: separate into two portals, two asking wfs (simulate partition of one asking wf)
  import ch.qos.logback.classic.Level
  import ch.qos.logback.classic.Logger

  import portals.api.dsl.ExperimentalDSL.*
  import portals.sql.*
  import portals.sql.QueryableWorkflow.clsToSqlTypeMapping
  import portals.sql.QueryableWorkflow.createDataWfPortal
  import portals.sql.QueryableWorkflow.createDataWorkflow

  val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
  logger.setLevel(Level.INFO)

  val rndTxnIDGenerator = scala.util.Random()
  rndTxnIDGenerator.setSeed(514)

  for (seed <- 1 to 200) {
    println("====== seed " + seed)

    val app = PortalsApp("app") {
      val generator1 = Generators.fromIteratorOfIterators[String](
        List(
          //        List(
          //          "INSERT INTO Book (id, title, \"year\", author) VALUES (1, 'Les Miserables', 1862, 0), (2, 'The Hunchback of Notre-Dame', 1829, 0)",
          //        ).iterator,
          //        List(
          //          "SELECT 'wf1', * FROM Book WHERE id IN (1, 2, 3, 4)",
          //        ).iterator
          List(
            "INSERT INTO Book (id, title, \"year\", author) VALUES (1, 'Les Miserables', 1862, 0), (2, 'The Hunchback of Notre-Dame', 1829, 0)," +
              "(3, 'The Last Day of a Condemned Man', 1829, 0), (4, 'The three Musketeers', 1844, 1)",
          ).iterator
//          List(
//            "INSERT INTO Book (id, title, \"year\", author) VALUES (1, 'Les Miserables', 1862, 0), (2, 'The Hunchback of Notre-Dame', 1829, 0)",
//          ).iterator
        ).iterator
      )

      val generator2 = Generators.fromIteratorOfIterators[String](
        List(
          //        List(
          //          "INSERT INTO Book (id, title, \"year\", author) VALUES (3, 'The Last Day of a Condemned Man', 1829, 0), (4, 'The three Musketeers', 1844, 1)",
          //        ).iterator,
          List(
            "SELECT 'wf2', * FROM Book WHERE id IN (1, 2, 3, 4)",
//            "SELECT 'wf2', * FROM Book WHERE id IN (1, 2)",
          ).iterator
        ).iterator
      )

      val tableName = "Book"

      // get all fields using reflection, then map to Sql type
      val clazz = implicitly[ClassTag[Book]].runtimeClass
      val fields = clazz.getDeclaredFields
      val fieldNames = fields.map(_.getName)
      val fieldTypes = fields.map(f => clsToSqlTypeMapping(f.getType))

      val bookPortal1 = QueryableWorkflow.createDataWfPortal("Book1")
      val bookPortal2 = QueryableWorkflow.createDataWfPortal("Book2")

      // data wf
      Workflows[Nothing, Nothing](tableName + "Wf")
        .source(Generators.empty.stream)
        .replier[Nothing](bookPortal1, bookPortal2) { _ =>
          ()
        } { q =>
          lazy val state = PerKeyState[Option[Book]](tableName, None)
          lazy val _txn = PerKeyState[Int]("txn", -1)

          q match
            case PreCommitOp(tableName, key, txnId, op) =>
              if _txn.get() == -1 || _txn.get() == txnId then
                println("precommit txn " + txnId + " key " + key + " success")
                _txn.set(txnId)
                reply(Result(STATUS_OK, op))
              else
                println("precommit txn " + txnId + " key " + key + " fail")
                reply(Result("error", List()))
            case SelectOp(tableName, key, txnId) =>
              val data = state.get()
              _txn.set(-1)
              if (data.isDefined)
                //              println("select " + key + " " + data.get)
                reply(Result(STATUS_OK, Book.toObjectArray(data.get)))
              else
                reply(Result(STATUS_OK, null))
            case InsertOp(tableName, data, key, txnId) =>
              state.set(Some(Book.fromObjectArray(data)))
              _txn.set(-1)
              //            println("inserted " + state.get().get)
              reply(Result(STATUS_OK, Array[Object]()))
            case RollbackOp(tableName, key, txnId) =>
              println("rollback txn " + txnId + " key " + key)
              _txn.set(-1)
              reply(Result(STATUS_OK, Array[Object]()))
            case null => reply(Result("error", List()))
        }
        .sink()
        .freeze()

      def genAskerWfPartition(generator: AtomicGeneratorRef[String], wfName: String, dataPortal: SQLPortal) =
        import java.math.BigDecimal

        Workflows[String, String](wfName)
          .source(generator.stream)
          .asker[FirstPhaseResult](dataPortal) { sql =>
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

            val ti = TableInfo(tableName, "", dataPortal, fieldNames, fieldTypes)

            calcite.registerTable(ti.tableName, ti.fieldTypes.toList.asJava, ti.fieldNames.toList.asJava, 0)
            calcite
              .getTable(ti.tableName)
              .setInsertRow(data => {
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
            calcite
              .getTable(ti.tableName)
              .setGetFutureByRowKeyFunc(key => {
                val intKey = key.asInstanceOf[BigDecimal].toBigInteger.intValueExact()
                val future =
                  ask(ti.portal)(PreCommitOp(ti.tableName, intKey, txnId, SelectOp(ti.tableName, intKey, txnId)))
                portalFutures = portalFutures :+ future
                new FutureWithResult(future, null)
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

            val emit = { (x: FirstPhaseResult) =>
              ctx.emit(x)
            }

            for (i <- 1 to tableScanCnt) {
              futureReadyCond.take

              // wait for the last one to awaitAll
              if i != tableScanCnt then awaitForFutureCond.put(1)
              else
                awaitAll[Result](portalFutures: _*) {
                  val results: List[Result] =
                    futures.asScala.map(_.future.asInstanceOf[Future[Result]].value.get).toList
                  val succeedOps = results.filter(_.status == STATUS_OK).map(_.data.asInstanceOf[SQLQueryEvent])

                  // TODO: made a partial commit example
                  if succeedOps.size != futures.size() then {
                    awaitForFutureCond.put(-1) // trigger execution failure
                    emit(FirstPhaseResult(-1, txnId, sql, false, succeedOps))
                  } else
                    emit(
                      FirstPhaseResult(
                        -1,
                        txnId,
                        sql,
                        true,
                        succeedOps,
                        futures,
                        awaitForFutureCond,
                        awaitForFinishCond,
                        result
                      )
                    )
                }
            }
          }
          .asker[String](dataPortal) { (preCommResult: FirstPhaseResult) =>
            val emit = { (x: String) =>
              ctx.emit(x)
            }

            if preCommResult.success then {
              println("txn " + preCommResult.txnID + " precommit succeed")
              var futures = List[Future[Result]]()
              preCommResult.succeedOps.foreach { op =>
                futures = futures :+ ask(dataPortal)(op)
              }
              awaitAll[Result](futures: _*) {
                for (i <- futures.indices) {
                  preCommResult.preparedOps.get(i).futureResult = futures(i).value.get.data.asInstanceOf[Array[Object]]
                }
                preCommResult.awaitForFutureCond.put(1)
                preCommResult.awaitForFinishCond.take()

                println("====== Result for " + preCommResult.sql + " ======")
                if !preCommResult.sql.startsWith("INSERT") && preCommResult.result.size() != 0 && preCommResult.result
                    .size() != 4
                then println("inconsistent result size " + preCommResult.result.size())
                else if !preCommResult.sql.startsWith("INSERT") then
                  println("consistent result size " + preCommResult.result.size())
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
                futures = futures :+ ask(dataPortal)(RollbackOp(op.tableName, op.key, op.txnId))
              }
              awaitAll[Result](futures: _*) {
                println("====== Abort txn " + preCommResult.txnID + " sql " + preCommResult.sql)
                emit("rollback")
              }
            }
          }
          .sink()
          .freeze()

      genAskerWfPartition(generator1, "asker1", bookPortal1)
      genAskerWfPartition(generator2, "asker2", bookPortal2)
    }

    // all query with more than one key should hold
    //  val system = Systems.interpreter()
    //  val system = new RandomInterpreter(Some(1)) // I2 I1 S2A S1
    //  val system = new RandomInterpreter(Some(2)) // I1 I2 S1A S2A
//    val system = new RandomInterpreter(Some(9)) // I1 I2 S1A S2A

    // size 2 -> all consistent -> out2 rate 6/200
    // size 4 -> all consistent -> out0 45 abort
    val system = new RandomInterpreter(Some(seed)) // I1 I2 S1A S2A
    system.launch(app)
    system.stepUntilComplete()
    system.shutdown()
  }
