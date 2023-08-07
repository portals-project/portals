package portals.libraries.sql.examples.sqltodataflow

import portals.libraries.sql.internals.COMMIT_QUERY
import portals.libraries.sql.internals.TxnQuery

object Data:
  private val queries =
    List(
      List(
        "INSERT INTO KVTable (k, v) Values (0, 0)",
        "INSERT INTO KVTable (k, v) Values (1, 1)",
        "INSERT INTO KVTable (k, v) Values (2, 2)",
        "INSERT INTO KVTable (k, v) Values (3, 0)",
        "INSERT INTO KVTable (k, v) Values (4, 1)",
        "INSERT INTO KVTable (k, v) Values (5, 2)",
      ),
      List(
        "SELECT * FROM KVTable WHERE k = 0",
        "SELECT * FROM KVTable WHERE k = 1",
        "SELECT * FROM KVTable WHERE k = 2",
        "SELECT * FROM KVTable WHERE k = 3",
        "SELECT * FROM KVTable WHERE k = 4",
        "SELECT * FROM KVTable WHERE k = 5",
      ),
      List(
        "SELECT * FROM KVTable WHERE v = 0 AND k in (0, 1, 2, 3, 4, 5)",
        "SELECT * FROM KVTable WHERE v = 1 AND k in (0, 1, 2, 3, 4, 5)",
        "SELECT * FROM KVTable WHERE v = 2 AND k in (0, 1, 2, 3, 4, 5)",
      )
    )

  def queryIterOfIter: Iterator[Iterator[String]] =
    queries.map(_.iterator).iterator

  // Note: Not interactive query here, possible
  private val transactionalQueries1 =
    List(
      List(
        TxnQuery("INSERT INTO KVTable (k, v) Values (0, 0)", 1),
      ),
      List(
        TxnQuery("INSERT INTO KVTable (k, v) Values (1, 0)", 1),
      ),
      List(
        TxnQuery(COMMIT_QUERY, 1),
      ),
      List(
        TxnQuery("SELECT * FROM KVTable WHERE k in (0, 1)", 2),
      ),
      List(
        TxnQuery(COMMIT_QUERY, 2),
      ),
    )

  private val transactionalQueries2 =
    List(
      List(
        TxnQuery("INSERT INTO KVTable (k, v) Values (0, 1)", 3),
      ),
      List(
        TxnQuery("INSERT INTO KVTable (k, v) Values (1, 1)", 3),
      ),
      List(
        TxnQuery(COMMIT_QUERY, 3),
      ),
      List(
        TxnQuery("SELECT * FROM KVTable WHERE k in (0, 1)", 4),
      ),
      List(
        TxnQuery(COMMIT_QUERY, 4),
      ),
    )

  def queryIterOfIterTxn1: Iterator[Iterator[TxnQuery]] =
    transactionalQueries1.map(_.iterator).iterator

  def queryIterOfIterTxn2: Iterator[Iterator[TxnQuery]] =
    transactionalQueries2.map(_.iterator).iterator
