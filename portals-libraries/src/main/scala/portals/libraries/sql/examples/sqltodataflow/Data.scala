package portals.libraries.sql.examples.sqltodataflow

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
