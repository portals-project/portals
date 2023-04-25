package portals.examples

import portals.api.builder.ApplicationBuilder
import portals.api.builder.TaskBuilder
import portals.api.dsl.DSL
import portals.application.task.PerTaskState
import portals.application.Application
import portals.system.Systems

/** Reservoir sampling
  *
  * This example shows how we can implement the Reservoir Sampling algorithm
  * using portals. The algorithm is described in
  * [[https://en.wikipedia.org/wiki/Reservoir_sampling#Algorithm_R this Wikipedia article]].
  */

@main def reservoirSamplingMain(): Unit =
  import portals.api.dsl.DSL.*

  val k = 20 // size of the reservoir

  // create an application builder
  val builder = ApplicationBuilder("application") // create an application builder

  // create a generator that generates a stream of integers from 1 to 100000 where 10000 is the size o the batch
  val generator = builder.generators.fromRange(1, 100000, 10000)

  // create a workflow that implements the reservoir sampling algorithm
  val _ = builder
    .workflows[Int, Int]("reservoirSampling")
    .source[Int](generator.stream)
    .init[Int] {
      val reservoir = PerTaskState[Array[Int]]("reservoir", Array.empty) // create a reservoir per task
      val count = PerTaskState[Int]("count", 0) // create a counter per task
      TaskBuilder.processor { element =>
        val i = count.get()
        count.set(count.get() + 1)
        if (i < k) { // if the reservoir is not full, add the element to the reservoir
          reservoir.set(reservoir.get() :+ element)
        } else { // if the reservoir is full, replace an element with the new one with a probability of k/i
          val j = scala.util.Random.nextInt(i + 1)
          if (j < k) {
            reservoir.set(reservoir.get().updated(j, element))
          }
        }
      }
    }
    .withOnAtomComplete { ctx ?=> // when the workflow is complete, emit the elements in the reservoir
      print(s"Final state: \n")
      val reservoir = PerTaskState[Array[Int]]("reservoir", Array.empty)
      reservoir.get().iterator.foreach { el => ctx.emit(el) }
      ctx.state.clear()
    }
    .logger() // log the elements in the reservoir
    .sink() // sink the stream
    .freeze() // freeze the workflow

  // build the application
  val application = builder
    .build()

  // launch the application
  val system = Systems.interpreter()
  system.launch(application)

  // run the application
  system.stepUntilComplete()
  system.shutdown()
