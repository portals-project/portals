package portals.examples

import scala.annotation.experimental

import portals.*

@experimental
@main def SplitterWorld(): Unit =
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  val app = PortalsApp("app") {
    val generator = Generators
      .fromIteratorOfIterators[Int](
        Iterator
          .range(0, 1024)
          .grouped(5)
          .map(_.iterator)
      )

    val splitter = Splitters.empty[Int](generator.stream)

    val split1 = Splitters.split(splitter, { _ % 2 == 0 })
    val split2 = Splitters.split(splitter, { _ % 2 == 1 })

    val wfeven = Workflows[Int, Int]("wfeven")
      .source(split1)
      .logger("EVEN STEVEN: ")
      .sink()
      .freeze()
    
    val wfodd = Workflows[Int, Int]("wfodd")
      .source(split2)
      .logger("ODD COD: ")
      .sink()
      .freeze()
  }

  ASTPrinter.println(app) // print the application AST

  val system = Systems.test()

  system.launch(app)

  system.stepUntilComplete()

  system.shutdown()
