package portals.sql.benchmark

import cask.*
import org.apache.calcite.sql.`type`.SqlTypeName
import org.apache.calcite.util.NlsString
import portals.api.builder.WorkflowBuilder
import portals.api.dsl.*
import portals.api.dsl.DSL.*
import portals.application.Workflow
import portals.application.generator.Generator
import portals.application.task.{PerKeyState, PerTaskState}
import portals.runtime.WrappedEvents
import portals.runtime.WrappedEvents.WrappedEvent
import portals.runtime.interpreter.InterpreterRuntime
import portals.sql.*
import portals.system.{InterpreterSystem, Systems}
import portals.util.{Future, Key}

import java.util
import java.util.concurrent.LinkedBlockingQueue
import scala.annotation.experimental
import scala.util.Random

case class MarkedSQL(reqId: Int, sql: String, pks: Map[String, Set[Int]], txnId: Int = -1, isCommit: Boolean = false)

@experimental
object BenchmarkBankTransfer extends App {

  var inputChan: REPLGeneratorAtom[MarkedSQL] = _
  var intSys: InterpreterSystem = _
  var resultStr: String = ""

  var opMap = Map[Int, Long]()
  var timeList = List[Long]()

  def stat() = {
    s"parsing: ${CalciteStat.getAvgParsingTime}\n" +
      s"validation: ${CalciteStat.getAvgValidationTime}\n" +
      s"planning: ${CalciteStat.getAvgPlanningTime}\n" +
      s"execution: ${CalciteStat.getAvgExecutionTime}\n"
  }

  var pendingRequests = Map[Int, Workload]()

  case class Workload(requestId: Int, txnId: Int, senderId: Int, receiverId: Int, amount: Int,
                      var senderBalance: Int, var receiverBalance: Int, var phase: Int) // 0: select 1: insert 2: commit

  val workload = 50000
  val sampleEvery = 250
  val baseBackOff = 3
  val backoff = 10
  val concurrency = 100
  var abortCnt = 0

  val rnd = Random(1)

  def run() = {
    var remainingWork = workload

    val userNum = 100

    var seenReqIds = Set[Int]()
    var seenTxnIds = Set[Int]()

    while (remainingWork > 0 || pendingRequests.nonEmpty) {
      var batch = List[Workload]()

      // add all pending requests
      pendingRequests.foreach((reqId, workload) => {
        batch = batch :+ workload
      })

      var minus = 0
      while (batch.size < Math.min(concurrency, remainingWork)) {
        minus += 1
        val sender = rnd.nextInt(userNum)
        var receiver = rnd.nextInt(userNum)
        while (receiver == sender) {
          receiver = rnd.nextInt(userNum)
        }

        var reqId = rnd.nextInt(2147483647)
        var txnId = rnd.nextInt(2147483647)
        while seenReqIds.contains(reqId) || seenTxnIds.contains(txnId) do {
          reqId = rnd.nextInt(2147483647)
          txnId = rnd.nextInt(2147483647)
        }
        seenReqIds += reqId
        seenTxnIds += txnId

        val workload = Workload(reqId, txnId, sender, receiver, rnd.nextInt(100), -1, -1, 0)
        batch = batch :+ workload
        pendingRequests += (workload.requestId -> workload)
        if rnd.nextInt(sampleEvery) == 0 then
          opMap += (workload.requestId -> System.nanoTime())
      }
      remainingWork -= minus

      // print batch
      //      println("batch:")
      //      batch.foreach(work => {
      //        println(s"[${work.requestId}:${work.txnId}] ${work.senderId}->${work.receiverId} ${work.amount} ${work.phase}")
      //      })

      for (work <- batch) {
        if work.phase < 0 then
          work.phase += 1
        else if work.phase == 0 then
          inputChan.add(MarkedSQL(
            work.requestId,
            s"SELECT * FROM Userr WHERE id=${work.senderId} OR id=${work.receiverId}",
            Map("Userr" -> Set(work.senderId, work.receiverId)),
            work.txnId,
            false
          ))
        else if work.phase == 1 then
          if work.senderBalance < work.amount then
            inputChan.add(MarkedSQL(
              work.requestId,
              s"INSERT INTO Userr VALUES (${work.senderId}, ${work.senderBalance + work.amount})",
              Map("Userr" -> Set(work.requestId)),
              work.txnId,
              false
            ))
          else
            inputChan.add(MarkedSQL(
              work.requestId,
              s"INSERT INTO Userr VALUES (${work.senderId}, ${work.senderBalance - work.amount}), (${work.receiverId}, ${work.receiverBalance + work.amount})",
              Map("Userr" -> Set(work.requestId)),
              work.txnId,
              false
            ))
        else if work.phase == 2 then
          inputChan.add(MarkedSQL(
            work.requestId,
            "",
            Map(),
            work.txnId,
            true
          ))
      }

      intSys.stepUntilComplete()
    }

  }

  initApp()
  runAll()

  object User extends DBSerializable[User]:
    def fromObjectArray(arr: List[Any]): User =
      User(
        arr(0).asInstanceOf[Integer],
        arr(1).asInstanceOf[Integer],
      )

    def toObjectArray(user: User): Array[Object] =
      Array[Object](
        user.id.asInstanceOf[Object],
        user.balance.asInstanceOf[Object],
      )

  case class User(
                   id: Integer,
                   balance: Integer
                 ) {
    override def toString: String =
      s"User($id, $balance)"
  }

  def timeListNPercentile(n: Int): Long = {
    val sorted = timeList.sorted
    sorted(sorted.length * n / 100)
  }

  def initApp() = {
    import ch.qos.logback.classic.{Level, Logger}
    import org.slf4j.LoggerFactory
    import portals.api.dsl.ExperimentalDSL.*
    import portals.sql.*

    import java.math.BigDecimal
    import scala.jdk.CollectionConverters.*

    val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    logger.setLevel(Level.INFO)

    val app = PortalsApp("app") {
      val table = QueryableWorkflow.createTable[User]("Userr", "id", User, true)
      val tableNameToPortal = Map(table.tableName -> table.portal)

      inputChan = new REPLGeneratorAtom[MarkedSQL]()
      val generator = Generators.generator[MarkedSQL](inputChan)

      def genWf(name: String): Workflow[MarkedSQL, String] =
        Workflows[MarkedSQL, String]("askerWf" + name)
          .source(generator.stream)
          .asker[FirstPhaseResult](table.portal) { markedSQL =>
            val pendingTxnPks = PerTaskState("pendingTxnPks", Map[Int, Map[String, Set[Int]]]())
            var pkRecordForCurrentTxn = pendingTxnPks.get().getOrElse(markedSQL.txnId, Map())
            // merge into record
            markedSQL.pks.foreachEntry((table, pks) => {
              val oldPks = pkRecordForCurrentTxn.getOrElse(table, Set())
              pkRecordForCurrentTxn += (table -> (oldPks ++ pks))
            })
            pendingTxnPks.set(pendingTxnPks.get() + (markedSQL.txnId -> pkRecordForCurrentTxn))

            if markedSQL.isCommit then
              val futures = PersistentList[Future[Result]]("portalFutures")
              for (elem <- pkRecordForCurrentTxn) {
                val tableName = elem._1
                val pks = elem._2
                val portal = tableNameToPortal(tableName)
                for (pk <- pks) {
                  val future = ask(portal)(CommitOp(tableName, pk, markedSQL.txnId))
                  futures.add(future)
                }
              }

              // TODO: clear workload
              awaitAll[Result](futures.asScala.toList: _*) {
                val workload = pendingRequests(markedSQL.reqId)
                //                println(s"request ${markedSQL.reqId} committed [${workload.senderId}:${workload.senderBalance} ${workload.receiverId}:${workload.receiverBalance} ${workload.amount}]")
                pendingRequests -= markedSQL.reqId
                if opMap.contains(markedSQL.reqId) then
                  timeList = timeList :+ (System.nanoTime() - opMap(markedSQL.reqId))
              }
            else
              val sql = markedSQL.sql

              //              println(sql)

              val futureReadyCond = PersistentLinkedBlockingQueue[Integer]("futureReadyCond")
              val awaitForFutureCond = PersistentLinkedBlockingQueue[Integer]("awaitForFutureCond")
              val awaitForFinishCond = PersistentLinkedBlockingQueue[Integer]("awaitForFinishCond")
              val tableOptCntCond = PersistentLinkedBlockingQueue[Integer]("tableOptCntCond")
              val result = PersistentList[Array[Object]]("result")
              val futures = PersistentList[FutureWithResult]("futures")
              val portalFutures = PersistentList[Future[Result]]("portalFutures")

              val calcite = new Calcite()
              calcite.printPlan = false

              val txnId = markedSQL.txnId

              val ti = table

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
                  portalFutures.add(future)
                  new FutureWithResult(future, null)
                })
              calcite
                .getTable(ti.tableName)
                .setGetFutureByRowKeyFunc(key => {
                  val intKey = key.asInstanceOf[BigDecimal].toBigInteger.intValueExact()
                  val future =
                    ask(ti.portal)(PreCommitOp(ti.tableName, intKey, txnId, SelectOp(ti.tableName, intKey, txnId)))
                  portalFutures.add(future)
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

              val tableScanCnt = tableOptCntCond.take
              //      println("tableScanCnt: " + tableScanCnt)

              val emit = { (x: FirstPhaseResult) =>
                ctx.emit(x)
              }

              for (i <- 1 to tableScanCnt) {
                futureReadyCond.take

                // wait for the last one to awaitAll
                if i != tableScanCnt then awaitForFutureCond.put(1)
                else
                  awaitAll[Result](portalFutures.asScala.toList: _*) {
                    val results: List[Result] =
                      futures.asScala.map(_.future.asInstanceOf[Future[Result]].value.get).toList
                    val succeedOps = results.filter(_.status == STATUS_OK).map(_.data.asInstanceOf[SQLQueryEvent])

                    // TODO: made a partial commit example
                    if succeedOps.size != futures.size() then {
                      awaitForFutureCond.put(-1) // trigger execution failure
                      emit(FirstPhaseResult(markedSQL.reqId, txnId, sql, false, succeedOps))
                    } else
                      emit(
                        FirstPhaseResult(
                          markedSQL.reqId,
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
          .asker[String](table.portal) { (preCommResult: FirstPhaseResult) =>
            val emit = { (x: String) =>
              ctx.emit(x)
            }

            if preCommResult.success then {
              //              println("txn " + preCommResult.txnID + " precommit succeed")
              var futures = List[Future[Result]]()
              preCommResult.succeedOps.foreach { op =>
                futures = futures :+ ask(table.portal)(op)
              }
              awaitAll[Result](futures: _*) {
                for (i <- futures.indices) {
                  //                  val dataWfResp = futures(i).value.get.data
                  preCommResult.preparedOps.get(i).futureResult = futures(i).value.get.data.asInstanceOf[Array[Object]]
                }
                preCommResult.awaitForFutureCond.put(1)
                preCommResult.awaitForFinishCond.take()

                val workload = pendingRequests(preCommResult.reqId)
                workload.phase += 1

                //                println("====== Result for " + preCommResult.sql + " ======")
                preCommResult.result.forEach(row => {
                  if workload.phase == 1 then
                    val uid = row(0).asInstanceOf[Int]
                    if uid == workload.senderId then workload.senderBalance = row(1).asInstanceOf[Int]
                    else if uid == workload.receiverId then workload.receiverBalance = row(1).asInstanceOf[Int]
                    else throw new Exception("unknown uid " + uid)
                  //                  println(java.util.Arrays.toString(row))
                  emit(java.util.Arrays.toString(row))
                })
              }
            } else {
              //        println("txn " + preCommResult.txnID + " precommit failed")
              var futures = List[Future[Result]]()
              preCommResult.succeedOps.foreach { op =>
                futures = futures :+ ask(table.portal)(RollbackOp(op.tableName, op.key, op.txnId))
              }

              //              if preCommResult.succeedOps.isEmpty then
              //                println("====== Abort sql " + preCommResult.sql + ", but no need to rollback ======")
              abortCnt += 1

              val workload = pendingRequests(preCommResult.reqId)
              //              workload.phase = 0
              workload.phase = -(baseBackOff+rnd.nextInt(1+backoff))

              awaitAll[Result](futures: _*) {
                //                println("====== Abort txn " + preCommResult.txnID + " sql " + preCommResult.sql)
                emit("rollback")
              }
            }
          }
          .sink()
          .freeze()

      genWf("1")
      //      genWf("2")
    }

    intSys = Systems.interpreter()
    //    intSys = new RandomInterpreter(Some(1))
    intSys.launch(app)
  }

  def runAll() = {
    val stTime = System.nanoTime()
    run()
    val edTime = System.nanoTime()

    println("abort rate: " + abortCnt.toDouble / timeList.length / sampleEvery)
    println("total time: " + (edTime - stTime).toDouble / 1_000_000_000 + "s")
    println("op/s: " + workload.toDouble * 1_000_000_000 / (edTime - stTime))
    println("avg time: " + timeList.sum.toDouble / timeList.length / 1_000_000 + "ms")
    println("50% time: " + timeListNPercentile(50).toDouble / 1_000_000 + "ms")
    println("90% time: " + timeListNPercentile(90).toDouble / 1_000_000 + "ms")
    println("99% time: " + timeListNPercentile(99).toDouble / 1_000_000 + "ms")
    println(stat())
  }
}
