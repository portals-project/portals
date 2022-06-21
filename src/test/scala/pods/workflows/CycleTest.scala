// package pods.workflows

// import org.junit.Test
// import org.junit.Ignore
// import org.junit.runner.RunWith
// import org.junit.runners.JUnit4
// import org.junit.Assert._
// import scala.collection.AnyStepper.AnyStepperSpliterator

// // Verify cycles between workflows behave as expected.
// @RunWith(classOf[JUnit4])
// class CycleTest:
  
//   @Ignore // current cycle implementation fails this test
//   @Test
//   def testCycle(): Unit = 
//     import pods.workflows.DSL.*

//     val builder = Workflows.builder().withName("cycle")

//     val cycleSrc = builder.cycle[Int]() // create cycle source

//     val src = builder
//       .source[Int]()
//       .withName("src")

//     val loop = builder
//       .merge(src, cycleSrc)
//       .flatMap[Int]{ ctx ?=> x =>
//         if (x > 0) List(x-1)
//         else List.empty
//       }
    
//     val _ = loop.intoCycle(cycleSrc)
    
//     val _ = loop.sink().withName("loop")

//     val wf = builder.build()
//     val system = Systems.local()
//     system.launch(wf)

//     val iref: IStreamRef[Int] = system.registry("cycle/src").resolve()
//     val oref: OStreamRef[Int] = system.registry.orefs("cycle/loop").resolve()

//     // create a test environment IRef
//     val testIRef = TestUtils.TestIStreamRef[Int]()

//     // subscribe testIRef to workflow
//     oref.subscribe(testIRef)

//     iref.submit(8)
//     iref.fuse()

//     system.shutdown()

//     assertTrue(testIRef.contains(8))
//     assertTrue(testIRef.contains(7))
//     assertTrue(testIRef.contains(6))
//     assertTrue(testIRef.contains(5))
//     assertTrue(testIRef.contains(4))
//     assertTrue(testIRef.contains(3))
//     assertTrue(testIRef.contains(2))
//     assertTrue(testIRef.contains(1))
//     assertFalse(testIRef.contains(0))
