package portals.examples

import scala.annotation.experimental

import portals.*

@experimental
@main def SplitterWorld(): Unit =
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  val extapp = PortalsApp("otherapp") {
    val generator = Generators("generator")
      .fromIteratorOfIterators[Int](
        Iterator
          .range(0, 1024)
          .grouped(5)
          .map(_.iterator)
      )

    val splitter = Splitters("splitter").empty[Int](generator.stream)
  }

  val app = PortalsApp("app") {
    val splitter = Registry.splitters.get[Int]("/otherapp/splitters/splitter")
    val split1 = Splits.split(splitter, { _ % 2 == 0 })
    val split2 = Splits.split(splitter, { _ % 2 == 1 })

    // val split1 = splitter.split { _ % 2 == 0 }
    // val split2 = splitter.split { _ % 2 == 1 }

    val wfeven = Workflows[Int, Int]("wfeven")
      .source(split1)
      .logger("EVEN STEVEN: ")
      .withOnAtomComplete {
        ctx.log.info("ATOM")
      }
      .withOnComplete {
        ctx.log.info("COMPLETE")
      }
      .sink()
      .freeze()

    val wfodd = Workflows[Int, Int]("wfodd")
      .source(split2)
      .logger("ODD COD: ")
      .withOnAtomComplete {
        ctx.log.info("ATOM")
      }
      .withOnComplete {
        ctx.log.info("COMPLETE")
      }
      .sink()
      .freeze()
  }

  // ASTPrinter.println(extapp) // print the application AST

  val system = Systems.test()

  system.launch(extapp)

  system.launch(app)

  system.stepUntilComplete()

  system.shutdown()
