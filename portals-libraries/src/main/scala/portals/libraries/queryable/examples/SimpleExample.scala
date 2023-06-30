package portals.libraries.queryable.examples

import portals.api.dsl.DSL.*
import portals.libraries.queryable.*
import portals.libraries.queryable.Queryable.*
import portals.libraries.queryable.Types.*
import portals.system.Systems

object SimpleExample extends App:
  case class Author(name: String, age: Int | Null) extends RowType

  val simpleExample = PortalsApp("simpleExample"):
    ////////////////////////////////////////////////////////////////////////////
    // Table
    ////////////////////////////////////////////////////////////////////////////

    val tableGenerator = Generators.fromList[CDC[Author]](
      List(
        Insert(Author("John", 30)),
        Insert(Author("Jane", 25)),
        Insert(Author("Jack", 40)),
        Remove(Author("John", null)),
      )
    )

    val table = Table[Author]("authors")

    val tableWorkflow = Workflows[CDC[Author], CDC[Author]]()
      .source(tableGenerator.stream)
      .table(table)
      .sink()
      .freeze()

    ////////////////////////////////////////////////////////////////////////////
    // Query
    ////////////////////////////////////////////////////////////////////////////

    val queryGenerator = Generators.fromList[String](
      List(
        "SELECT * FROM authors WHERE name = 'John'",
        "SELECT * FROM authors WHERE name = 'Jane'",
        "SELECT * FROM authors WHERE name = 'Jule'",
      )
    )

    val queryWorkflow = Workflows[String, String]()
      .source(queryGenerator.stream)
      .query(table.ref)
      .sink()
      .freeze()

  //////////////////////////////////////////////////////////////////////////////
  // Run the example
  //////////////////////////////////////////////////////////////////////////////
  val system = Systems.test()
  system.launch(simpleExample)
  system.stepUntilComplete()
  system.shutdown()
