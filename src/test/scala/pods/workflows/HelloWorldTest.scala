package pods.workflows

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

@RunWith(classOf[JUnit4])
class HelloWorldTest:
  @Test
  def testHelloWorld() =
    import java.util.concurrent.atomic.AtomicReference

    val output = new AtomicReference[List[Any]](List.empty)

    val helloWorld = "Hello, World!"

    val builder = Workflows
      .builder()
      .withName("wf")

    val flow = builder
      .source[String]()
      .withName("input")
      .withLogger()
      .sink()
      .withName("output")

    val builder2 = Workflows
      .builder()
      .withName("wf2")

    val flow2 = builder2
      .source[String]()
      .withName("input")
      .processor[String] {
        ctx ?=> s =>
          var continue = true
          while continue do
            val oldV = output.get
            val newV = s :: oldV
            if output.compareAndSet(oldV, newV) then
              continue = false
      }
      .sink()
      .withName("output")

    val wf = builder.build()
    val wf2 = builder2.build()

    val system = Systems.local()
    system.launch(wf)
    system.launch(wf2)

    val iref2: IStreamRef[String] = system.registry("wf2/input").resolve()
    val oref: OStreamRef[String] = system.registry.orefs("wf/output").resolve()

    val _ = oref.subscribe(iref2)

    val iref: IStreamRef[String] = system.registry("wf/input").resolve()
    iref.submit(helloWorld)
    iref.fuse()

    system.shutdown()

    println(output.get)

    assertTrue(output.get().head == helloWorld)
    assertFalse(output.get().head == "")
