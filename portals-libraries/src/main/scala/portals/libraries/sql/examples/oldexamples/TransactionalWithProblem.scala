package portals.libraries.sql.examples.oldexamples

import java.util.concurrent.LinkedBlockingQueue

import scala.annotation.experimental
import scala.reflect.ClassTag

import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.application.task.PerKeyState
import portals.application.AtomicGeneratorRef
import portals.libraries.sql.*
import portals.libraries.sql.calcite.*
import portals.libraries.sql.queryable.*
import portals.libraries.sql.queryable.QueryableWorkflow.*
import portals.system.Systems
import portals.util.Future

/** Simulate concurrent queries, in this case insert on key 0 will block select
  * on key (1, 0), precommit on key 1 will be rolled back
  */
@experimental
object TransactionalConflictWithProblem extends App:

  import scala.jdk.CollectionConverters.*

  import org.slf4j.LoggerFactory

  // TODO: separate into two portals, two asking wfs (simulate partition of one asking wf)
  import ch.qos.logback.classic.Level
  import ch.qos.logback.classic.Logger

  import portals.api.dsl.ExperimentalDSL.*
  import portals.libraries.sql.*
  import portals.libraries.sql.queryable.*

  val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
  logger.setLevel(Level.INFO)

  val rndTxnIDGenerator = scala.util.Random()
  rndTxnIDGenerator.setSeed(514)

  for (seed <- 1 to 200) {

    // more sql statements
    val app = PortalsApp("app") {
      val generator1 = Generators.fromIteratorOfIterators[String](
        List(
          // NOTE: actually upsert
          List(
            "INSERT INTO Book (id, title, \"year\", author) VALUES (1, 'Les Miserables', 1862, 0), (2, 'The Hunchback of Notre-Dame', 1829, 0)," +
              "(3, 'The Hunchback of Notre-Dame', 1829, 0), (4, 'The Hunchback of Notre-Dame', 1829, 0)",
//            "INSERT INTO Book (id, title, \"year\", author) VALUES (1, 'Les Miserables', 1862, 0), (2, 'The Hunchback of Notre-Dame', 1829, 0)",
          ).iterator,
        ).iterator
      )

      val generator2 = Generators.fromIteratorOfIterators[String](
        List(
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
              println("select txn " + txnId + " key " + key)
              val data = state.get()
              _txn.set(-1)
              if (data.isDefined)
                //              println("select " + key + " " + data.get)
                reply(Result(STATUS_OK, Book.toObjectArray(data.get)))
              else
                reply(Result(STATUS_OK, null))
            case InsertOp(tableName, data, key, txnId) =>
              println("insert txn " + txnId + " key " + key + " data " + data)
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
          .asker[String](dataPortal) { sql =>
            val futureReadyCond = new LinkedBlockingQueue[Integer]()
            val awaitForFutureCond = new LinkedBlockingQueue[Integer]()
            val awaitForFinishCond = new LinkedBlockingQueue[Integer]()
            val tableOptCntCond = new LinkedBlockingQueue[Integer]()
            val result = new java.util.ArrayList[Array[Object]]()
            val futures = new java.util.ArrayList[FutureWithResult]()
            var portalFutures = List[Future[Result]]()

            val calcite = new Calcite()
            calcite.printPlan = false

            val ti = TableInfo(tableName, "", dataPortal, fieldNames, fieldTypes)
            calcite.registerTable(ti.tableName, ti.fieldTypes.toList.asJava, ti.fieldNames.toList.asJava, 0)
            calcite
              .getTable(ti.tableName)
              .setInsertRow(data => {
                // TODO: assert pk always Int
                val future = ask(ti.portal)(InsertOp(ti.tableName, data.toList, data(0).asInstanceOf[Int]))
                portalFutures = portalFutures :+ future
                new FutureWithResult(future, null)
              })
            calcite
              .getTable(ti.tableName)
              .setGetFutureByRowKeyFunc(key => {
                val future =
                  ask(ti.portal)(SelectOp(ti.tableName, key.asInstanceOf[BigDecimal].toBigInteger.intValueExact()))
                portalFutures = portalFutures :+ future
                new FutureWithResult(future, null)
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
              //        println("try future ready consume")
              futureReadyCond.take
              //        println("future ready consume done")

              // wait for the last one to awaitAll
              if i != tableOptCnt then awaitForFutureCond.put(1)
              else
                awaitAll[Result](portalFutures: _*) {
                  futures.forEach(f => {
                    val data = f.future.asInstanceOf[Future[Result]].value
                    f.futureResult = f.future.asInstanceOf[Future[Result]].value.get.data.asInstanceOf[Array[Object]]
                  })

                  awaitForFutureCond.put(1) // allow SQL execution to start

                  awaitForFinishCond.take() // wait for SQL execution to finish

                  println("====== Result for " + sql + " ======")
                  if !sql.startsWith("INSERT") && (result.size() != 0 && result.size() != 4) then
                    println("inconsistent result size " + result.size())
                  else if !sql.startsWith("INSERT") then println("consistent result size " + result.size())
                  result.forEach(row => println(java.util.Arrays.toString(row)))

                  result.forEach(row => {
                    emit(java.util.Arrays.toString(row))
                  })
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
    //  val system = new RandomInterpreter(Some(15)) // Problem Found!

    // I2 Q2 -> inconsistency 47/200
    // I4 Q4 -> inconsistency 93/200 -> result size 45
    val system = Systems.test() // I1 I2 S1A S2A
    system.launch(app)
    system.stepUntilComplete()
    system.shutdown()
  }
