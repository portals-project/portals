package portals.examples.actor

import scala.annotation.experimental

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Test

import portals.system.Systems

@RunWith(classOf[JUnit4])
class ActorTest:

  @Test
  @experimental
  def testFibonacci(): Unit =
    import portals.api.dsl.DSL.*

    import ActorEvents.ActorMessage
    import ActorEvents.ActorCreate
    import Fibonacci.FibReply
    import Fibonacci.FibActors.initBehavior

    val FIB_N = 21
    val testBehavior = ActorTestUtils.TestBehavior(initBehavior(FIB_N))

    val config = ActorConfig.default
      .replace("logging", true)

    val app = PortalsApp("Fibonacci") {
      val generator = Generators.signal[ActorMessage](ActorCreate(ActorRef.fresh(), testBehavior.behavior))
      val wf = ActorWorkflow(generator.stream, config)
    }

    val system = Systems.test()
    system.launch(app)
    system.stepUntilComplete()
    system.shutdown()

    testBehavior
      .receiveAssert(FibReply(10946))
      .isEmptyAssert()
