package portals.libraries.queryable.examples

import portals.api.dsl.DSL.*
import portals.libraries.queryable.*
import portals.libraries.queryable.Queryable.*
import portals.libraries.queryable.Types.*
import portals.system.Systems

object SimpleExample extends App:
  case class Author(name: String, age: Option[Int] = None) extends RowType

  val simpleExample = PortalsApp("simpleExample"):
    ////////////////////////////////////////////////////////////////////////////
    // Table
    ////////////////////////////////////////////////////////////////////////////

    val tableGenerator = Generators.fromListOfLists[CDC[Author]](
      List(
        List(Insert(Author("John", Some(30)))),
        List(Insert(Author("Jane", Some(25)))),
        List(Insert(Author("Jack", Some(40)))),
        List(Remove(Author("John"))),
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

    val queryGenerator = Generators.fromListOfLists[String](
      List(
        List("SELECT * FROM authors WHERE name = 'John'"),
        List("SELECT * FROM authors WHERE name = 'Jane'"),
        List("SELECT * FROM authors WHERE name = 'Jule'"),
        List("SELECT * FROM authors WHERE name = 'Jack'"),
      )
    )

    val queryWorkflow = Workflows[String, Any]()
      .source(queryGenerator.stream)
      .map(SQLQuery(_))
      .query(table.ref)
      .map(_.result)
      .logger()
      .sink()
      .freeze()

  //////////////////////////////////////////////////////////////////////////////
  // Run the example
  //////////////////////////////////////////////////////////////////////////////
  val system = Systems.test()
  system.launch(simpleExample)
  system.stepUntilComplete()
  system.shutdown()
