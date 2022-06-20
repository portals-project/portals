package pods.workflows

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

@RunWith(classOf[JUnit4])
class HelloWorldTest:

  @Test
  def testHelloWorld(): Unit =
    val helloWorld = "Hello, World!"

    val builder = Workflows
      .builder()
      .withName("wf")

    val flow = builder
      .source[String]()
      .withName("input")
      .map[String] { x => x }
      .sink()
      .withName("output")

    val wf = builder.build()

    val system = Systems.local()
    system.launch(wf)

    val iref: IStreamRef[String] = system.registry("wf/input").resolve()
    val oref: OStreamRef[String] = system.registry.orefs("wf/output").resolve()

    // create a test environment IRef
    val testIRef = TestUtils.TestIStreamRef[String]()

    val _ = oref.subscribe(testIRef)

    iref.submit(helloWorld)
    iref.fuse()

    system.shutdown()

    testIRef.receiveAssert(helloWorld)