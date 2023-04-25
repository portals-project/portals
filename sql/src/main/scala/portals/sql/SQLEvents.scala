package portals.sql

import java.util.concurrent.LinkedBlockingQueue

trait SQLQueryEvent {
  def tableName: String
  def key: Int
  def txnId: Int
}

case class PreCommitOp(tableName: String, key: Int, txnId: Int, op: SQLQueryEvent) extends SQLQueryEvent

case class SelectOp(tableName: String, key: Int, txnId: Int = -1) extends SQLQueryEvent

case class InsertOp(tableName: String, data: List[Any], key: Int, txnId: Int = -1) extends SQLQueryEvent

case class RollbackOp(tableName: String, key: Int, txnId: Int = -1) extends SQLQueryEvent

case class Result(status: String, data: Any)

val STATUS_OK = "ok"

case class FirstPhaseResult(
    txnID: Int,
    sql: String,
    success: Boolean,
    succeedOps: List[SQLQueryEvent], // for rollback or commit
    preparedOps: java.util.List[FutureWithResult] = null,
    // 1 for success, -1 for failure
    awaitForFutureCond: LinkedBlockingQueue[Integer] = null,
    awaitForFinishCond: LinkedBlockingQueue[Integer] = null,
    result: java.util.List[Array[Object]] = null,
)

trait DBSerializable[T] {
  def toObjectArray(t: T): Array[Object]
  def fromObjectArray(arr: List[Any]): T
}
