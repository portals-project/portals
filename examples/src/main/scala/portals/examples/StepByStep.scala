package portals.examples

import scala.annotation.experimental

import portals.*
import portals.api.builder.TaskBuilder

/** Step By Step
  *
  * Lyrics from Step by Step of the Album Step By Step by Eddie Rabbit, 1981.
  */
@experimental
@main def StepByStep(): Unit =
  import portals.DSL.*

  import portals.ExperimentalDSL.*

  val app = PortalsApp("StepByStep") {

    val lyrics = List(
      "First step, ask her out and treat her like a lady",
      "Second step, tell her she's the one you're dreaming of",
      "Third step, take her in your arms and never let her go",
      "Don't you know that step by step, step by step",
      "You'll win her love",
      "Second step",
      "Third step",
      "Don't you know step by step, step by step",
      "--",
    )

    val generator =
      Generators.fromIteratorOfIterators[Unit](Iterator.continually(Iterator.single(())))

    // The workflow takes steps over atoms, and for every new atom it will output the next line in the lyric to the log
    Workflows[Unit, Nothing]("stepper")
      .source(generator.stream)
      .task(TaskBuilder.processor { _ => log.info(lyrics(0)) })
      .withStep(TaskBuilder.processor { _ => log.info(lyrics(1)) })
      .withStep(TaskBuilder.processor { _ => log.info(lyrics(2)) })
      .withStep(TaskBuilder.processor { _ => log.info(lyrics(3)) })
      .withStep(TaskBuilder.processor { _ => log.info(lyrics(4)) })
      .withStep(TaskBuilder.processor { _ => log.info(lyrics(5)) })
      .withStep(TaskBuilder.processor { _ => log.info(lyrics(6)) })
      .withStep(TaskBuilder.processor { _ => log.info(lyrics(7)) })
      .withStep(TaskBuilder.processor { _ => log.info(lyrics(8)) })
      .empty[Nothing]()
      .sink()
      .freeze()
  }

  val system = Systems.test()

  system.launch(app)

  Range(0, 128).foreach { _ =>
    system.step()
  }

  system.shutdown()
