package portals.examples.tutorial

import portals.api.builder.ApplicationBuilder
import portals.api.builder.TaskBuilder
import portals.api.dsl.DSL
import portals.application.task.PerTaskState
import portals.application.Application
import portals.system.Systems

/** Fibonacci Numbers
  *
  * This example shows how to calculate Fibonacci numbers using portals.
  * Fibonacci numbers are a sequence of numbers in which each number is the sum
  * of the two preceding ones.
  */

@main def fibonacciNumbersMain(): Unit =
  import portals.api.dsl.DSL._

  val n = 20 // number of Fibonacci numbers to calculate

  val builder = ApplicationBuilder("application")

  val generator = builder.generators.fromIterator(Iterator.iterate((0, 1)) {
    case (prev, current) => (current, prev + current)
  }.map(_._1).take(n))


  val _ = builder
    .workflows[Int, List[Int]]("fibonacci")
    .source(generator.stream)
    .init[List[Int]] {
      val fibs = PerTaskState[Array[Int]]("fibonacci", Array.empty)
      val count = PerTaskState[Int]("count", 0)
      TaskBuilder.processor { _ =>
        count.set(count.get() + 1)
        if(count.get() == 1) fibs.set(Array(0))
        else if(count.get() == 2) fibs.set(Array(0, 1))
        else fibs.set(fibs.get() :+ (fibs.get()(count.get() - 2) + fibs.get()(count.get() - 3)))


      }
    }
    .withOnAtomComplete { ctx ?=> // when the workflow is complete, emit the calculated Fibonacci numbers
      val fibs = PerTaskState[Array[Int]]("fibonacci", Array.empty)
      ctx.emit(fibs.get().toList)
      ctx.state.clear()
    }
    .logger()
    .sink()
    .freeze()

  val application = builder.build()

  val system = Systems.interpreter()
  system.launch(application)

  system.stepUntilComplete()
  system.shutdown()
