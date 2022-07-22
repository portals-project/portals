package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

@RunWith(classOf[JUnit4])
class ApplicationTest:

  @Test
  def applicationTest(): Unit =
    import portals.DSL.*

    val wfStream = AtomicStream[Int](
      path = "/app/workflow/stream",
      name = "stream",
    )

    val generatorStream = AtomicStream[Int](
      path = "/app/generator/stream",
      name = "stream",
    )

    val workflow = new Workflow[Int, Int](
      path = "/app/workflow",
      name = "workflow",
      consumes = AtomicStreamRef("/app/generator/stream"),
      stream = AtomicStreamRef[Int]("/app/workflow/stream"),
      sequencer = None,
      tasks = Map(
        ("mapper", Tasks.map[Int, Int](_ + 1)),
        ("logger", Tasks.map[Int, Int] { ctx ?=> x => { ctx.log.info(x.toString()); x } })
      ),
      sources = Map(("src", Tasks.identity[Int])),
      sinks = Map(("sink", Tasks.identity[Int])),
      connections = List(
        "src" -> "mapper",
        "mapper" -> "logger",
        "logger" -> "sink"
      )
    )

    val iter = Iterator(1, 2, 3)
      .map(Generator.Event(_))
      .concat(Iterator(Generator.Atom))
      .concat(Iterator(4, 5, 6).map(Generator.Event(_)))
      .concat(Iterator(Generator.Atom, Generator.Seal))
    val underlyingGenerator = new Generator[Int] {
      override def generate() = iter.next()
      override def hasNext() = iter.hasNext
    }

    val generator = new AtomicGenerator[Int](
      path = "/app/generator",
      name = "generator",
      stream = AtomicStreamRef("/app/generator/stream"),
      generator = underlyingGenerator,
    )

    val app = new Application(
      path = "/app",
      name = "app",
      workflows = List(workflow),
      generators = List(generator),
      streams = List(wfStream, generatorStream)
    )

    val system = Systems.syncLocal()

    system.launch(app)

    system.stepAll()

    system.shutdown()
