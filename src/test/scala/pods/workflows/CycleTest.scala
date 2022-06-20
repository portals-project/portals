package pods.workflows

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import scala.collection.AnyStepper.AnyStepperSpliterator

// Verify cycles between workflows behave as expected.
@RunWith(classOf[JUnit4])
class CycleTest:
  @Test
  def testCycle() = 
    import java.util.concurrent.atomic.AtomicReference

    val output = new AtomicReference[List[Any]](List.empty)
    val loopNum = new AtomicReference[Int](2)
    val builder = Workflows.builder().withName("cycle")

    val cycleSrc = builder.cycle[Int]() // create cycle source

    val src = builder
      .source[Int]()
      .withName("src")

    val loop = builder
      .merge(src, cycleSrc)
      .processor[Int]{
        ctx ?=> i =>
          if loopNum.get > 0 then
            println(s"loop: $i")
            println(s"loopNum: ${loopNum.get}")
            loopNum.set(loopNum.get() - 1)
            ctx.emit(i + 1)
          else
            println(s"loop: $i")
            println(s"loopNum: ${loopNum.get}")
            var continue = true
            while continue do
              val oldV = output.get
              val newV = i :: oldV
              if output.compareAndSet(oldV, newV) then
                continue = false
      }
      .intoCycle(cycleSrc)

    val wf = builder.build()
    val system = Systems.local()
    system.launch(wf)

    val iref: IStreamRef[Int] = system.registry("cycle/src").resolve()

    iref.submit(1)
    iref.fuse()

    system.shutdown()

    println(output.get)
    assertTrue(output.get.contains(3))
    assertFalse(output.get.contains(2))
    assertFalse(output.get.contains(1))

