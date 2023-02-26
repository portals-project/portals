package portals

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test

import portals.test.*

@RunWith(classOf[JUnit4])
class SplitterTest:

  @Test
  def splitTest(): Unit =
    val tester1 = new TestUtils.Tester[Int]()
    val tester2 = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilder("app")

    // producer
    val generator = builder.generators.fromRange(0, 256, 5)

    val splitter = builder.splitters.empty[Int](generator.stream)

    // split 1
    val split1 = builder.splits.split(splitter, _ % 2 == 0)

    // split 2
    val split2 = builder.splits.split(splitter, _ % 2 == 1)

    // consumer 1
    val _ = builder
      .workflows[Int, Int]("consumingWorkflow1")
      .source(split1)
      .task(tester1.task)
      .sink()
      .freeze()

    // consumer 2
    val _ = builder
      .workflows[Int, Int]("consumingWorkflow2")
      .source(split2)
      .task(tester2.task)
      .sink()
      .freeze()

    val app = builder.build()

    val system = Systems.test()

    system.launch(app)

    system.stepUntilComplete()

    system.shutdown()

    val received1 = tester1.receiveAll()
    val receivedAtoms1 = tester1.receiveAllAtoms().filter(_.nonEmpty)
    val receivedWrapped1 = tester1.receiveAllWrapped()
    val received2 = tester2.receiveAll()
    val receivedAtoms2 = tester2.receiveAllAtoms().filter(_.nonEmpty)
    val receivedWrapped2 = tester2.receiveAllWrapped()

    val testData = Iterator.range(0, 256).toList
    val testDataAtoms = Iterator.range(0, 256).grouped(5).map(_.toList).toList
    val testData1 = testData.filter(_ % 2 == 0)
    val testData2 = testData.filter(_ % 2 == 1)
    val testDataAtoms1 = testDataAtoms.map(_.filter(_ % 2 == 0)).filter(_.nonEmpty)
    val testDataAtoms2 = testDataAtoms.map(_.filter(_ % 2 == 1)).filter(_.nonEmpty)

    // TODO: this could be a useful helper function for tests in other tests
    def wrapper[T](seqseq: Seq[Seq[T]]): Seq[Seq[TestUtils.Tester.WrappedEvent[T]]] =
      seqseq
        .map(_.map(x => TestUtils.Tester.Event(x)).appended(TestUtils.Tester.Atom))
        .appended(Seq(TestUtils.Tester.Seal))

    val testDataWrapped1 = wrapper(testDataAtoms1).flatten
    val testDataWrapped2 = wrapper(testDataAtoms2).flatten

    // 1. all events have been produced and consumed
    assertEquals(testData1, received1)
    assertEquals(testData2, received2)

    // 2. the atoms are split correctly, in the correct groups
    assertEquals(testDataAtoms1, receivedAtoms1)
    assertEquals(testDataAtoms2, receivedAtoms2)

    // 3. the Atom and Seal events are correct
    assertEquals(testDataWrapped1, receivedWrapped1)
    assertEquals(testDataWrapped2, receivedWrapped2)
