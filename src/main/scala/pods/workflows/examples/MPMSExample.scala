package pods.workflows.examples

/** Multiple Producer Multiple Subscriber Event Count
 *
 * This example counts the event number from multiple upstream producers, which
 * concurrently emit events to multiple downstream subscribers. This example
 * can also be used as a test to show event handling for each processor is
 * serial in our runtime.
 */
@main def MultiProducerExample() =
  import pods.workflows.*

  val builder = Workflows
    .builder()
    .withName("wf")

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
        if (i % 5000 == 0) println(prefix + i)
        //      println(prefix + i)
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
    .from(
      mergedSource
    )
    .processor(eventCount)
    .processor(printInterval("output2 "))
    .sink()
    .withName("output2")

  val wf = builder.build()

  val system = Systems.local()
  system.launch(wf)

  val iref1: IStreamRef[Int] = system.registry("wf/input1").resolve()
  val iref2: IStreamRef[Int] = system.registry("wf/input2").resolve()

  val t1 = (new Thread() {
    override def run(): Unit = for (i <- 1 to 10000) {
      iref1.submit(i)
      iref1.fuse()
    }
  })
  val t2 = (new Thread() {
    override def run(): Unit = for (i <- 1 to 10000) {
      iref2.submit(i)
      iref2.fuse()
    }
  })

  t1.start()
  t2.start()
  t1.join()
  t2.join()

  system.shutdown()
