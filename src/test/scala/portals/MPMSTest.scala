package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore

/** Multiple Producer Multiple Subscriber Event Count
 *
 * This test counts the event number from multiple upstream producers, which
 * concurrently emit events to multiple downstream subscribers. This test can
 * also show event handling for each processor is serial in our runtime.
 */
@RunWith(classOf[JUnit4])
class MultiProducerTest:

  // @Ignore
  @Test
  def testMultiProducer(): Unit =
    val builder = Portals
      .builder("wf")

    val source1 = builder
      .source[Int]()
      .withName("input1")

    val source2 = builder
      .source[Int]()
      .withName("input2")

    def eventCount: TaskContext[Int, Int] ?=> Int => Unit = ctx ?=>
      _ => {
        val key = "k"
        ctx.state.get(key) match
          case Some(n) =>
            ctx.state.set(key, n.asInstanceOf[Int] + 1)
          case None =>
            ctx.state.set(key, 1)
        ctx.emit(ctx.state.get(key).get.asInstanceOf[Int])
      }

    def printInterval(prefix: String): TaskContext[Int, Int] ?=> Int => Unit =
      ctx ?=>
        i => {
          // if (i % 5000 == 0) println(prefix + i)
          ctx.emit(i)
        }

    val mergedSource = builder
      .merge(source1, source2)

    builder
      .from(mergedSource)
      .processor(eventCount)
      .processor(printInterval("output1 "))
      .sink()
      .withName("output1")

    builder
      .from(mergedSource)
      .processor(eventCount)
      .processor(printInterval("output2 "))
      .sink()
      .withName("output2")

    val wf = builder.build()

    val system = Systems.syncLocal()
    system.launch(wf)

    val iref1: IStreamRef[Int] = system.registry("wf/input1").resolve()
    val iref2: IStreamRef[Int] = system.registry("wf/input2").resolve()
    val oref1: OStreamRef[Int] = system.registry.orefs("wf/output1").resolve()
    val oref2: OStreamRef[Int] = system.registry.orefs("wf/output2").resolve()

    // create a test environment IRef
    val testIRef1 = TestUtils.TestPreSubmitCallback[Int]()
    oref1.setPreSubmitCallback(testIRef1)
    val testIRef2 = TestUtils.TestPreSubmitCallback[Int]()
    oref2.setPreSubmitCallback(testIRef2)

    val t1 = (new Thread() {
      override def run(): Unit = for (i <- 1 to 1000) {
        iref1.submit(i)
        iref1.fuse()
      }
    })
    val t2 = (new Thread() {
      override def run(): Unit = for (i <- 1 to 1000) {
        iref2.submit(i)
        iref2.fuse()
      }
    })

    t1.start()
    t2.start()
    t1.join()
    t2.join()

    system.stepAll()
    system.shutdown()

    // check that the output is correct
    for (i <- 1 to 2000) {
      assertTrue(testIRef1.contains(i))
      assertTrue(testIRef2.contains(i))
    }
    assertFalse(testIRef1.contains(0))
    assertFalse(testIRef2.contains(0))
    assertFalse(testIRef1.contains(2001))
    assertFalse(testIRef2.contains(2001))
