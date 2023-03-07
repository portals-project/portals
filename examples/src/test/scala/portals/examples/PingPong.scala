package portals.examples

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test

import portals.api.builder.ApplicationBuilder
import portals.test.TestUtils
import portals.Systems

@RunWith(classOf[JUnit4])
class PingPongTest:

  @Test
  def testPingPong(): Unit =
    import portals.api.dsl.DSL.*

    case class Ping(i: Int)
    case class Pong(i: Int)

    val tester = new TestUtils.Tester[Pong]()

    val system = Systems.interpreter()

    {
      val pinger = ApplicationBuilder("pinger")

      val sequencer = pinger.sequencers("sequencer").random[Pong]()

      val _ = pinger
        .workflows[Pong, Ping]("workflow")
        .source[Pong](sequencer.stream)
        .map { case Pong(i) => Ping(i) }
        .sink[Ping]()
        .freeze()

      val pingerApp = pinger.build()

      system.launch(pingerApp)
    }

    {
      val ponger = ApplicationBuilder("ponger")

      val extStream = ponger.registry.streams.get[Ping]("/pinger/workflows/workflow/stream")

      val pongerwf = ponger
        .workflows[Ping, Pong]("ponger")
        .source[Ping](extStream)
        .map { case Ping(i) => Pong(i - 1) }
        .filter(_.i > 0)
        // .logger()
        .task(tester.task)
        .sink[Pong]()
        .freeze()

      val extSequencer = ponger.registry.sequencers.get[Pong]("/pinger/sequencers/sequencer")
      val _ = ponger.connections.connect(pongerwf.stream, extSequencer)

      val pongerApp = ponger.build()

      system.launch(pongerApp)
    }

    {
      val builder = ApplicationBuilder("generator")

      val generator = builder.generators.fromList(List(Pong(128)))

      val extSequencer = builder.registry.sequencers.get[Pong]("/pinger/sequencers/sequencer")

      val connection = builder.connections.connect(generator.stream, extSequencer)

      val app = builder.build()

      system.launch(app)
    }

    system.stepUntilComplete()

    system.shutdown()

    assertEquals((127 until 0 by -1).map(Pong(_)).toList, tester.receiveAll())
