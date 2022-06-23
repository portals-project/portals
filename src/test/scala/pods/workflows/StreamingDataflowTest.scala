package pods.workflows

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

/** Streaming Dataflow tests
  *
  * These tests show how we can model Streaming Dataflow in our model.
  * 
  * Streaming Dataflow is straight-forward to implement in our model, Pods 
  * Workflows are a hybrid of dataflow and actors. We build a streaming 
  * pipeline from sources, apply transformations on the data through the 
  * processor abstraction, and end in a sink. The processor abstraction can 
  * then be used to implement all higher-level operators that are common in 
  * streaming dataflow, such as Map, FlatMap, Windowing, etc.
  */

  
/** Incremental Word Count
  * 
  * The incremental word count test computes the wordcount of a stream of 
  * lines of text. For each new ingested word, the updated wordcount is emitted.
*/
@RunWith(classOf[JUnit4])
class IncrementalWordCountTest:

  @Test
  def testIncrementalWordCount(): Unit =
    import pods.workflows.DSL.*

    // our map function takes an string and splits it to produce a list of words
    val mapper: String => Seq[(String, Int)] =
      line => line.split("\\s+").map(w => (w, 1))

    // our reduce function takes two mapped elements and adds the counts together
    val reducer: ((String, Int), (String, Int)) => (String, Int) = 
      ((x, y) =>  (x._1, x._2 + y._2))

    // one naive implementation is to use local state for storing the counts
    val builder = Workflows
      .builder()
      .withName("workflow") // give name
    
    val flow = builder
      // input is a stream of lines of text, we give it the name "text"
      .source[String]()
      .withName("text")
      // each line is splitted on whitespaces
      .flatMap(_.split("\\s+"))
      // we create tuples for each word, with the second element being the number of occurrences of the word
      .map(word => (word, 1))
      // keyBy groups every tuple by the first element
      .keyBy(_._1)
      // we compute the incrementally updated wordcount for each word using the 
      // state provided by ctx
      // explicit reduce task
      .map(ctx ?=> (x: (String, Int)) => 
        val (key, value) = (x._1, x._2)
        ctx.state.get(x._1)  match
          case Some(count) => 
            ctx.state.set(key, count.asInstanceOf[Int] + value)
            (key, count.asInstanceOf[Int] + value)
          case None =>
            ctx.state.set(key, value)
            (key, value)
      )
      // .withLogger()
      .sink()
      .withName("counts")
      
    val workflow = builder.build()

    val system = Systems.local()
    system.launch(workflow)

    val testData = "the quick brown fox jumps over the lazy dog"

    // create a test environment IRef
    val testIRef = TestUtils.TestIStreamRef[(String, Int)]()

    // to get a reference of the workflow we look in the registry
    // resolve takes a shared ref and creates an owned ref that points to the shared ref
    val iref = system.registry[String]("workflow/text").resolve()
    val oref = system.registry.orefs[(String, Int)]("workflow/counts").resolve()
    oref.subscribe(testIRef)

    iref.submit(testData)
    iref.fuse()

    system.shutdown()

    // check that the output is correct
    assertTrue(testIRef.contains(("the", 1)))
    assertTrue(testIRef.contains(("the", 2)))
    assertTrue(testIRef.contains(("quick", 1)))
    assertTrue(testIRef.contains(("brown", 1)))
    assertTrue(testIRef.contains(("fox", 1)))
    assertTrue(testIRef.contains(("jumps", 1)))
    assertTrue(testIRef.contains(("over", 1)))
    assertTrue(testIRef.contains(("lazy", 1)))
    assertTrue(testIRef.contains(("dog", 1)))
