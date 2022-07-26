package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore

@RunWith(classOf[JUnit4])
class PingPongTest:

  @Ignore
  @Test
  def testPingPong(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Pong]()

    val system = Systems.syncLocal()

    case class Ping(i: Int)
    case class Pong(i: Int)

    val pinger = ApplicationBuilders
      .application("pinger")

    val sequencer = pinger.sequencers.random[Pong]("sequencer")

    val generator = pinger.generators.fromList("generator", List(Pong(128)))

    val _ = pinger.connections.connect("connection", generator.stream, sequencer)

    val pingerwf = pinger
      .workflows[Pong, Ping]("pinger")
      .source[Pong](sequencer.stream)
      .map { case Pong(i) => Ping(i) }
      .sink[Ping]()
      .freeze()

    val pingerApp = pinger.build()

    val ponger = ApplicationBuilders
      .application("ponger")

    val pongerwf = ponger
      .workflows[Ping, Pong]("ponger")
      .source[Ping](pingerwf.stream)
      .map { case Ping(i) => Pong(i - 1) }
      .filter(_.i > 0)
      // .logger()
      .task(tester.task)
      .sink[Pong]()
      .freeze()

    val extSequencer = ponger.registry.sequencers.get[Pong]("/pinger/sequencer")
    val _ = ponger.connections.connect("connection", pongerwf.stream, extSequencer.resolve())

    val pongerApp = ponger.build()

    system.launch(pingerApp)
    system.launch(pongerApp)
    system.stepAll()

    assertEquals((128 until 0 by -1).toList, tester.receiveAll())
