package portals.sql

import java.math.BigDecimal
import java.util
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Collectors

import scala.annotation.experimental

import org.apache.calcite.sql.`type`.SqlTypeName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Test

import ch.qos.logback.classic.Logger

import portals.api.builder.ApplicationBuilder
import portals.api.builder.TaskBuilder
import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.application.task.AskerTaskContext
import portals.application.task.PerKeyState
import portals.application.task.PerTaskState
import portals.application.task.TaskStates
import portals.application.AtomicPortalRef
import portals.sql.*
import portals.system.Systems
import portals.test.TestUtils
import portals.util.Future




@experimental
//@RunWith(classOf[JUnit4])
object Transaction extends App {

//  @Test
//  def main(): Unit = {
    import portals.api.dsl.ExperimentalDSL.*

    import scala.jdk.CollectionConverters.*

    import org.slf4j.LoggerFactory
    import ch.qos.logback.classic.Logger
    import ch.qos.logback.classic.Level

    val tester = new TestUtils.Tester[String]()

    val app = PortalsApp("app") {


      val bookPortal = QueryableWorkflow.createDataWfPortal("bookPortal")
      val authorPortal = QueryableWorkflow.createDataWfPortal("authorPortal")

      val tableNameToPortal = Map(
        "Book" -> bookPortal,
        "Author" -> authorPortal,
      )

      // return two SQL queries for each iterator
      val generator = Generators.fromList(
        List(
          "INSERT INTO Author (id, fname, lname) VALUES (0, 'Victor', 'Hugo')",
          "SELECT * FROM Author WHERE id IN (0, 1)",
//          "INSERT INTO Author (id, fname, lname) VALUES (1, 'Alexandre', 'Dumas')",
        )
      )

      val rndTxnIDGenerator = scala.util.Random()
      rndTxnIDGenerator.setSeed(514)

      Workflows[String, FirstPhaseResult]("askerWf")
        .source(generator.stream)
        // TODO: question, why only book portal is enough?
        .asker[FirstPhaseResult](bookPortal) { sql =>
          val futureReadyCond = new LinkedBlockingQueue[Integer]()
          val awaitForFutureCond = new LinkedBlockingQueue[Integer]()
          val awaitForFinishCond = new LinkedBlockingQueue[Integer]()
          val tableScanCntCond = new LinkedBlockingQueue[Integer]()
          val result = new util.ArrayList[Array[Object]]()
          val futures = new util.ArrayList[FutureWithResult]()

          var portalFutures = List[Future[Result]]()

          val calcite = new Calcite()
          calcite.printPlan = false
          calcite.registerTable(
            "Book",
            List(SqlTypeName.INTEGER, SqlTypeName.VARCHAR, SqlTypeName.INTEGER, SqlTypeName.INTEGER).asJava,
            List("id", "title", "year", "author").asJava,
            0
          )
          calcite.registerTable(
            "Author",
            List(SqlTypeName.INTEGER, SqlTypeName.VARCHAR, SqlTypeName.VARCHAR).asJava,
            List("id", "fname", "lname").asJava,
            0
          )

          val txnId = rndTxnIDGenerator.nextInt(1000000)

          // needs to know how many times are select asking called for different tables
          calcite
            .getTable("Book")
            .setInsertRow(data => {
              val future = ask(bookPortal)(
                PreCommitOp(
                  "Book",
                  data(0).asInstanceOf[Int],
                  txnId,
                  InsertOp("Book", data.toList, data(0).asInstanceOf[Int], txnId)
                )
              )
              portalFutures = portalFutures :+ future
              new FutureWithResult(future, null)
            })
          calcite
            .getTable("Book")
            .setGetFutureByRowKeyFunc(key => {
              val intKey = key.asInstanceOf[BigDecimal].toBigInteger.intValueExact()
              val future = ask(bookPortal)(PreCommitOp("Book", intKey, txnId, SelectOp("Book", intKey, txnId)))
              portalFutures = portalFutures :+ future
              new FutureWithResult(future, null)
            })
          calcite
            .getTable("Author")
            .setInsertRow(data => {
              val future = ask(authorPortal)(
                PreCommitOp(
                  "Author",
                  data(0).asInstanceOf[Int],
                  txnId,
                  InsertOp("Author", data.toList, data(0).asInstanceOf[Int], txnId)
                )
              )
              portalFutures = portalFutures :+ future
              new FutureWithResult(future, null)
            })
          calcite
            .getTable("Author")
            .setGetFutureByRowKeyFunc(key => {
              val intKey = key.asInstanceOf[BigDecimal].toBigInteger.intValueExact()
              val future = ask(authorPortal)(PreCommitOp("Author", intKey, txnId, SelectOp("Author", intKey, txnId)))
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
          println("tableScanCnt: " + tableScanCnt)

          val emit = { (x: FirstPhaseResult) =>
            ctx.emit(x)
          }

          for (i <- 1 to tableScanCnt) {
//            println("try future ready consume")
            futureReadyCond.take
//            println("future ready consume done")

            // wait for the last one to awaitAll
            if i != tableScanCnt then awaitForFutureCond.put(1)
            else
              awaitAll[Result](portalFutures: _*) {
                val results: List[Result] = futures.asScala.map(_.future.asInstanceOf[Future[Result]].value.get).toList
                val succeedOps = results.filter(_.status == STATUS_OK).map(_.data.asInstanceOf[SQLQueryEvent])
//                val failedOps = results.filter(_.status != STATUS_OK).map(_.data.asInstanceOf[SQLQueryEvent])

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
        .asker[FirstPhaseResult](bookPortal) { (preCommResult: FirstPhaseResult) =>
          val emit = { (x: FirstPhaseResult) =>
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
                println(util.Arrays.toString(row))
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
            }
          }
        }
        .sink()
        .freeze()


      QueryableWorkflow.createDataWorkflow[Book]("Book", bookPortal, Book)
      QueryableWorkflow.createDataWorkflow[Author]("Author", authorPortal, Author)
    }

    val system = Systems.interpreter()
    system.launch(app)
    system.stepUntilComplete()
    system.shutdown()

  }

