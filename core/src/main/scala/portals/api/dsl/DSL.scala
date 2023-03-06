package portals

import scala.annotation.experimental

/** DSL extensions for Portals, convenient shorthands for building applications.
  *
  * @see
  *   [[portals.DSL.PortalsApp]]
  */
object DSL:
  //////////////////////////////////////////////////////////////////////////////
  // Task DSL
  //////////////////////////////////////////////////////////////////////////////
  /** Convenient shorthands for using the TaskContext. */

  /** Summon the given TaskContext.
    *
    * This is a generic method, so it can be used with any TaskContext. Here we
    * use a generic task context instead, and use the return type of the
    * dependent contextual generic task context to obtain a more specific task
    * context type. This can for example return a MapTaskContext when used
    * within a MapTask, or a regular ProcessorTaskContext when used within a
    * ProcessorTask.
    *
    * @example
    *   {{{
    * val task = TaskBuilder.map[Int, Int]{ x =>
    *   // access the ctx to log a message
    *   ctx.log.info("Hello from map task")
    *   // access the ctx to access the task state
    *   ctx.state.get(0)
    *   x
    * }
    *   }}}
    *
    * @return
    *   The contextual task context.
    */
  def ctx(using gctx: GenericGenericTaskContext): gctx.type = gctx

  /** Emit an event.
    *
    * To be accessed within a task.
    *
    * @example
    *   {{{
    * val task = TaskBuilder.processor[Int, Int]{ x =>
    *   // emit an event
    *   emit(x + 1)
    * }
    *   }}}
    *
    * @param event
    *   The event to emit.
    * @tparam T
    *   The Task's type of the input value.
    * @tparam U
    *   The Task's type of the output value.
    */
  def emit[T, U](event: U)(using EmittingTaskContext[T, U]) = summon[EmittingTaskContext[T, U]].emit(event)

  /** Task state.
    *
    * To be accessed within a task.
    *
    * See also: Portals TaskStates, PerKeyState, PerTaskState for more
    * convenient state access, typed state.
    *
    * @example
    *   {{{
    * val task = TaskBuilder.processor[Int, Int] { x =>
    *   state.set(0, state.get(0).getOrElse(0) + x)
    *   emit(x)
    * }
    *   }}}
    *
    * @return
    *   The state of the task.
    */
  def state(using StatefulTaskContext) = summon[StatefulTaskContext].state

  /** Logger.
    *
    * To be accessed within a task.
    *
    * @example
    *   {{{
    * val task = TaskBuilder.map[Int, Int]{ x =>
    *   // log the message
    *   log.info(x.toString())
    *   x
    * }
    *   }}}
    *
    * @return
    *   The logger.
    * @tparam T
    *   The Task's type of the input value.
    * @tparam U
    *   The Task's type of the output value.
    */
  def log[T, U](using LoggingTaskContext[T, U]) = summon[LoggingTaskContext[T, U]].log

  //////////////////////////////////////////////////////////////////////////////
  // Builder DSL
  //////////////////////////////////////////////////////////////////////////////
  extension (gb: GeneratorBuilder) {

    /** Generator with a single output value `sig`.
      *
      * @example
      *   {{{val sig = Generators.signal[Int](1)}}}
      * @param sig
      *   The output value.
      * @tparam T
      *   The type of the output value.
      */
    def signal[T](sig: T): AtomicGeneratorRef[T] = gb.fromList(List[T](sig))
  }

  //////////////////////////////////////////////////////////////////////////////
  // Portals DSL
  //////////////////////////////////////////////////////////////////////////////
  /** Convenient shorthands for using the portals, askers, repliers, etc. */

  /** Ask the `portal` with `req`, returns a future of the reply.
    *
    * @example
    *   {{{
    * val myApp = PortalsApp("myApp") {
    *   val portal = Registry.portals.get[String, String]("/path/to/external/portal")
    *   val asker = TaskBuilder.asker[Int, Int, String, String](portal) { x =>
    *   val future = ask(portal)(x.toString())
    *   await(future) {
    *     log.info(future.value.get)
    *   }
    * }
    * }
    *   }}}
    *
    * @param portal
    *   The portal to ask.
    * @param req
    *   The request to send.
    * @return
    *   A future of the reply.
    * @tparam T
    *   The Task's type of the input value.
    * @tparam U
    *   The Task's type of the output value.
    * @tparam Req
    *   The Portal's type of the request.
    * @tparam Rep
    *   The Portal's type of the reply.
    */
  def ask[T, U, Req, Rep](using ctx: AskerTaskContext[T, U, Req, Rep])(portal: AtomicPortalRefKind[Req, Rep])(
      req: Req
  ): Future[Rep] =
    ctx.ask(portal)(req)

  /** Reply with `rep` to the handled request.
    *
    * @example
    *   {{{
    * val myApp = PortalsApp("myApp") {
    *   val portal = Registry.portals.get[String, String]("/path/to/external/portal")
    *   val asker = TaskBuilder.asker[Int, Int, String, String](portal) { x =>
    *   val future = ask(portal)(x.toString())
    *   await(future) {
    *     log.info(future.value.get)
    *   }
    * }
    * }
    *   }}}
    *
    * @param rep
    *   The reply to send.
    * @tparam T
    *   The Task's type of the input value.
    * @tparam U
    *   The Task's type of the output value.
    * @tparam Req
    *   The Portal's type of the request.
    * @tparam Rep
    *   The Portal's type of the reply.
    */
  def reply[T, U, Req, Rep](using
      ctx: ReplierTaskContext[T, U, Req, Rep]
  )(rep: Rep): Unit = ctx.reply(rep)

  /** Await the completion of the `future` and then execute continuation `f`.
    *
    * @example
    *   {{{
    * // Example 1
    * Await(future) {log.info(future.value.get)}
    *   }}}
    *
    * @example
    *   {{{
    * // Example 2
    * val myApp = PortalsApp("myApp") {
    *   val portal = Registry.portals.get[String, String]("/path/to/external/portal")
    *   val asker = TaskBuilder.asker[Int, Int, String, String](portal) { x =>
    *     val future = ask(portal)(x.toString())
    *     Await(future) {
    *       log.info(future.value.get)
    *     }
    *   }
    * }
    *   }}}
    *
    * @param future
    *   The future to await.
    * @param f
    *   The function to execute.
    * @tparam T
    *   The Task's type of the input value.
    * @tparam U
    *   The Task's type of the output value.
    * @tparam Req
    *   The Portal's type of the request.
    * @tparam Rep
    *   The Portal's type of the reply.
    */
  def Await[T, U, Req, Rep](future: Future[Rep])(f: AskerTaskContext[T, U, Req, Rep] ?=> Unit)(using
      ctx: AskerTaskContext[T, U, Req, Rep]
  ): Unit = ctx.await(future)(f)

  extension [Rep](future: Future[Rep]) {

    /** Await the completion of the `future` and then execute continuation `f`.
      *
      * @example
      *   {{{
      * // Example 1
      * future.await {log.info(future.value.get)}
      *   }}}
      *
      * @example
      *   {{{
      * // Example 2
      * val myApp = PortalsApp("myApp") {
      *   val portal = Registry.portals.get[String, String]("/path/to/external/portal")
      *   val asker = TaskBuilder.asker[Int, Int, String, String](portal) { x =>
      *     val future = ask(portal)(x.toString())
      *     future.await {
      *       log.info(future.value.get)
      *     }
      *   }
      * }
      *   }}}
      *
      * @param f
      *   The function to execute.
      * @tparam T
      *   The Task's type of the input value.
      * @tparam U
      *   The Task's type of the output value.
      * @tparam Req
      *   The Portal's type of the request.
      */
    def await[T, U, Req](
        f: AskerTaskContext[T, U, Req, Rep] ?=> Unit
    )(using ctx: AskerTaskContext[T, U, Req, Rep]): Unit =
      ctx.await(future)(f)
  }

  //////////////////////////////////////////////////////////////////////////////
  // Convenient Builder DSL
  //////////////////////////////////////////////////////////////////////////////
  /** Convenient interface for using the ApplicationBuilder */

  /** Summon the RegistryBuilder.
    *
    * Use this method to create a registry within the context of an
    * applicationbuilder.
    *
    * @example
    *   {{{
    * val myApp = PortalsApp("myApp") {
    *   val externalStream =
    *     Registry.streams.get[String]("/path/to/external/stream")
    *
    *   val externalSequencer =
    *     Registry.sequencers.get[String]("/path/to/external/sequencer")
    *
    *   val externalPortal =
    *     Registry.portals.get[String, String]("/path/to/external/portal")
    *
    *   val externalSplitter =
    *     Registry.splitters.get[String]("/path/to/external/splitter")
    * }
    *   }}}
    *
    * @return
    *   The registry builder.
    */
  def Registry(using ab: ApplicationBuilder): RegistryBuilder = ab.registry

  /** Summon the WorkflowBuilder.
    *
    * Use this method to create a workflow within the context of an
    * applicationbuilder.
    *
    * If the name is omitted, then the generator will be anonymous and a
    * generated identifier will be used instead.
    *
    * @example
    *   {{{
    * val myApp = PortalsApp("myApp") {
    * val generator = Generators.signal[String]("hello world")
    * val workflow = Workflows[String, String]()
    *   .source(generator.stream)
    *   .logger()
    *   .sink()
    *   .freeze()
    * }
    *   }}}
    *
    * @return
    *   The workflow builder.
    */
  def Workflows[T, U]()(using ab: ApplicationBuilder): WorkflowBuilder[T, U] = ab.workflows[T, U]()

  /** Summon the WorkflowBuilder.
    *
    * Use this method to create a workflow within the context of an
    * applicationbuilder.
    *
    * @example
    *   {{{
    * val myApp = PortalsApp("myApp") {
    * val generator = Generators.signal[String]("hello world")
    * val workflow = Workflows[String, String]("name")
    *   .source(generator.stream)
    *   .logger()
    *   .sink()
    *   .freeze()
    * }
    *   }}}
    *
    * @param name
    *   The name of the workflow.
    * @return
    *   The workflow builder.
    */
  def Workflows[T, U](name: String)(using ab: ApplicationBuilder): WorkflowBuilder[T, U] = ab.workflows[T, U](name)

  /** Summon the GeneratorBuilder.
    *
    * Use this method to create a generator within the context of an
    * applicationbuilder.
    *
    * @example
    *   {{{
    * val myApp = PortalsApp("myApp") {
    *   val generator = Generators("name").fromList(List("hello", "world")
    * }
    *   }}}
    *
    * @param name
    *   The name of the generator.
    * @return
    *   The generator builder.
    */
  def Generators(name: String)(using ab: ApplicationBuilder): GeneratorBuilder = ab.generators(name)

  /** Summon the GeneratorBuilder.
    *
    * Use this method to create a generator within the context of an
    * applicationbuilder.
    *
    * If the name is omitted, then the generator will be anonymous and a
    * generated identifier will be used instead.
    *
    * @example
    *   {{{
    * val myApp = PortalsApp("myApp") {
    *   val generator = Generators.fromList(List("hello", "world")
    * }
    *   }}}
    *
    * @return
    *   The generator builder.
    */
  def Generators(using ab: ApplicationBuilder): GeneratorBuilder = ab.generators

  /** Summon the SequencerBuilder.
    *
    * A sequencer sequences a set of (mutable) input streams to a single output
    * stream. New inputs to the sequencer can be added via the [[Connections]]
    * `connect` method. You can access the output stream of a sequencer via its
    * `stream` member.
    *
    * Use this method to create a sequencer within the context of an
    * applicationbuilder.
    *
    * @example
    *   {{{
    * val myApp = PortalsApp("myApp") {
    *   val gen1 = Generators.signal[String]("hello")
    *   val gen2 = Generators.signal[String]("world")
    *   val sequencer = Sequencers.random[String]()
    *   val _ = Connections.connect(gen1.stream, sequencer)
    *   val _ = Connections.connect(gen2.stream, sequencer)
    * }
    *   }}}
    *
    * @param name
    *   The name of the sequencer.
    * @return
    *   The sequencer builder.
    */
  def Sequencers(name: String)(using ab: ApplicationBuilder): SequencerBuilder = ab.sequencers(name)

  /** Summon the SequencerBuilder.
    *
    * A sequencer sequences a set of (mutable) input streams to a single output
    * stream. New inputs to the sequencer can be added via the [[Connections]]
    * `connect` method. You can access the output stream of a sequencer via its
    * `stream` member.
    *
    * Use this method to create a sequencer within the context of an
    * applicationbuilder.
    *
    * If the name is omitted, then the sequencer will be anonymous and a
    * generated identifier will be used instead.
    *
    * @example
    *   {{{
    * val myApp = PortalsApp("myApp") {
    *   val gen1 = Generators.signal[String]("hello")
    *   val gen2 = Generators.signal[String]("world")
    *   val sequencer = Sequencers.random[String]()
    *   val _ = Connections.connect(gen1.stream, sequencer)
    *   val _ = Connections.connect(gen2.stream, sequencer)
    * }
    *   }}}
    *
    * @return
    *   The sequencer builder.
    */
  def Sequencers(using ab: ApplicationBuilder): SequencerBuilder = ab.sequencers

  /** Summon the SplitterBuilder.
    *
    * The splitter splits an input atomic stream into multiple output streams.
    * The splits are mutable, we can add a new split via the [[Splits]] `split`
    * method, this method takes the splitter and a predicate that determines
    * which elements are kept for the split, and returns a stream of the new
    * split.
    *
    * Use this method to create a splitter within the context of an
    * applicationbuilder.
    *
    * @example
    *   {{{
    *  val myApp = PortalsApp("myApp") {
    *    val generator = Generators.fromList[String](List("Hello", "World"))
    *    // Create a splitter that splits the generator stream
    *    val splitter = Splitters.empty[String](generator.stream)
    *    // Add a split that only contains the "Hello" elements
    *    val helloSplit = Splits.split(splitter, _ == "Hello")
    *    // Add a split that only contains the "World" elements
    *    val worldSplit = Splits.split(splitter, _ == "World")
    *    // Log the output
    *    val wf = Workflows[String, String]()
    *      .source(helloSplit)
    *      .logger()
    *      .sink()
    *      .freeze()
    * }
    *   }}}
    *
    * @param name
    *   The name of the splitter.
    * @return
    *   The splitter builder.
    */
  def Splitters(name: String)(using ab: ApplicationBuilder): SplitterBuilder = ab.splitters(name)

  /** Summon the SplitterBuilder.
    *
    * The splitter splits an input atomic stream into multiple output streams.
    * The splits are mutable, we can add a new split via the [[Splits]] `split`
    * method, this method takes the splitter and a predicate that determines
    * which elements are kept for the split, and returns a stream of the new
    * split.
    *
    * Use this method to create a splitter within the context of an
    * applicationbuilder.
    *
    * If the name is omitted, then the sequencer will be anonymous and a
    * generated identifier will be used instead.
    *
    * @example
    *   {{{
    *  val myApp = PortalsApp("myApp") {
    *    val generator = Generators.fromList[String](List("Hello", "World"))
    *    // Create a splitter that splits the generator stream
    *    val splitter = Splitters.empty[String](generator.stream)
    *    // Add a split that only contains the "Hello" elements
    *    val helloSplit = Splits.split(splitter, _ == "Hello")
    *    // Add a split that only contains the "World" elements
    *    val worldSplit = Splits.split(splitter, _ == "World")
    *    // Log the output
    *    val wf = Workflows[String, String]()
    *      .source(helloSplit)
    *      .logger()
    *      .sink()
    *      .freeze()
    * }
    *   }}}
    *
    * @return
    *   The splitter builder.
    */
  def Splitters(using ab: ApplicationBuilder): SplitterBuilder = ab.splitters

  /** Summon the SplitBuilder.
    *
    * The split builder is used to create a new stream (split) from a splitter.
    * The splitter consumes an atomic stream, and splits can be added to this
    * splitter via the [[Splits]] `split` method. The split method takes a
    * predicate to filter out the events for the split. The method returns an
    * atomic stream of the new split.
    *
    * Use this method to create a split within the context of an
    * applicationbuilder.
    *
    * @example
    *   {{{
    *  val myApp = PortalsApp("myApp") {
    *    val generator = Generators.fromList[String](List("Hello", "World"))
    *    // Create a splitter that splits the generator stream
    *    val splitter = Splitters.empty[String](generator.stream)
    *    // Add a split that only contains the "Hello" elements
    *    val helloSplit = Splits.split(splitter, _ == "Hello")
    *    // Add a split that only contains the "World" elements
    *    val worldSplit = Splits.split(splitter, _ == "World")
    * }
    *   }}}
    *
    * @return
    *   The split builder.
    */
  def Splits(using ab: ApplicationBuilder): SplitBuilder = ab.splits

  /** Summon the ConnectionBuilder.
    *
    * The connection builder is used to connect an atomic stream to a sequencer.
    *
    * Use this method to create a connection within the context of an
    * applicationbuilder.
    *
    * @example
    *   {{{
    * val myApp = PortalsApp("myApp") {
    *   val gen1 = Generators.signal[String]("hello")
    *   val gen2 = Generators.signal[String]("world")
    *   val sequencer = Sequencers.random[String]()
    *   val _ = Connections.connect(gen1.stream, sequencer)
    *   val _ = Connections.connect(gen2.stream, sequencer)
    * }
    *   }}}
    *
    * @return
    *   The connection builder.
    */
  def Connections(using ab: ApplicationBuilder): ConnectionBuilder = ab.connections

  /** Summon the PortalBuilder.
    *
    * The portal builder is used to create portals. Portals have a request type,
    * and a reply type, and a name. Some workflow must be designated to handle
    * the requests of a portal, this is done via creating an `asker` task. The
    * asker task has two event handlers, one for regular inputs to the task, and
    * one for the requests. Another workflow may connect to the portal to send
    * requests, this is done via a `replyer` task. The replyer task has a single
    * event handler for the regular incoming events. Sending a request by the
    * `ask` method will create a future, the asking task can await the
    * completion of this future, which registers a continuation which is
    * executed when the reply is received.
    *
    * Use this method to create a portal within the context of an
    * applicationbuilder.
    *
    * @example
    *   {{{
    * // Example 1
    * val myApp = PortalsApp("myApp") {
    *   // Create a Portal with name "portal" which has request type Int, and Reply type String
    *   val portal = Portal[Int, String]("portal")
    * }
    *   }}}
    *
    * @example
    *   {{{
    * // Example 2
    * /////////////////////////////////////////////////////////////////////////
    * // Queries
    * /////////////////////////////////////////////////////////////////////////
    * case class Query()
    * case class QueryReply(x: Int)
    *
    * val myApp = PortalsApp("myApp") {
    *   ///////////////////////////////////////////////////////////////////////
    *   // Aggregator
    *   ///////////////////////////////////////////////////////////////////////
    *   // Create a Portal which has request type Query, and Reply type QueryReply
    *   val portal = Portal[Query, QueryReply]("portal")
    *
    *   // Atom(1, 2, 3, 4), Atom(1, 2, 3, 4), ...
    *   val generator = Generators.fromListOfLists(List.fill(10)(List(1, 2, 3, 4)))
    *
    *   // The replying workflow
    *   val aggregator = Workflows[Int, Nothing]("aggregator")
    *   .source(generator.stream)
    *   // The replier consumes the stream of Ints, aggregates it. It replies to
    *   // requests with the latest aggregated value.
    *   .replier[Nothing](portal) { x => // regular input event (Int)
    *   // Aggregate the value
    *   val sum = PerTaskState("sum", 0)
    *   sum.set(sum.get() + x)
    *   } { case Query() => // handle portal request
    *   // Reply with the latest aggregated value
    *   reply(QueryReply(PerTaskState("sum", 0).get()))
    *   }
    *   .sink()
    *   .freeze()
    *
    *   ///////////////////////////////////////////////////////////////////////
    *   // Query
    *   ///////////////////////////////////////////////////////////////////////
    *   // Atom(0), Atom(0), ...
    *   val queryTrigger = Generators.fromListOfLists(List.fill(10)(List(0)))
    *
    *   // The requesting workflow
    *   val queryWorkflow = Workflows[Int, Nothing]("queryWorkflow")
    *   .source(queryTrigger.stream)
    *   // The asker consumes a stream of 0s, for each event it will send a Query
    *   // to the portal, and wait for and print the reply.
    *   .asker[Nothing](portal) { x =>
    *   // query the replier
    *   val future: Future[QueryReply] = ask(portal)(Query())
    *   future.await { // wait for the reply to complete
    *   future.value.get match
    *   case QueryReply(x) =>
    *   // print the aggregate to log
    *   ctx.log.info(x.toString())
    *   }
    *   }
    *   .sink()
    *   .freeze()
    *
    * }
    *
    * val system = Systems.test()
    * system.launch(myApp)
    * system.stepUntilComplete()
    * system.shutdown()
    *   }}}
    */
  def Portal[T, R](name: String)(using ab: ApplicationBuilder): AtomicPortalRef[T, R] = ab.portals.portal(name)

  /** Summon the PortalBuilder.
    *
    * The portal builder is used to create portals. Portals have a request type,
    * and a reply type, and a name. Some workflow must be designated to handle
    * the requests of a portal, this is done via creating an `asker` task. The
    * asker task has two event handlers, one for regular inputs to the task, and
    * one for the requests. Another workflow may connect to the portal to send
    * requests, this is done via a `replyer` task. The replyer task has a single
    * event handler for the regular incoming events. Sending a request by the
    * `ask` method will create a future, the asking task can await the
    * completion of this future, which registers a continuation which is
    * executed when the reply is received.
    *
    * Use this method to create a portal within the context of an
    * applicationbuilder.
    *
    * @example
    *   {{{
    * // Example 1
    * val myApp = PortalsApp("myApp") {
    *   // Create a Portal with name "portal" which has request type Int, and Reply type String
    *   val portal = Portal[Int, String]("portal")
    * }
    *   }}}
    *
    * @example
    *   {{{
    * // Example 2
    * /////////////////////////////////////////////////////////////////////////
    * // Queries
    * /////////////////////////////////////////////////////////////////////////
    * case class Query()
    * case class QueryReply(x: Int)
    *
    * val myApp = PortalsApp("myApp") {
    *   ///////////////////////////////////////////////////////////////////////
    *   // Aggregator
    *   ///////////////////////////////////////////////////////////////////////
    *   // Create a Portal which has request type Query, and Reply type QueryReply
    *   val portal = Portal[Query, QueryReply]("portal")
    *
    *   // Atom(1, 2, 3, 4), Atom(1, 2, 3, 4), ...
    *   val generator = Generators.fromListOfLists(List.fill(10)(List(1, 2, 3, 4)))
    *
    *   // The replying workflow
    *   val aggregator = Workflows[Int, Nothing]("aggregator")
    *   .source(generator.stream)
    *   // The replier consumes the stream of Ints, aggregates it. It replies to
    *   // requests with the latest aggregated value.
    *   .replier[Nothing](portal) { x => // regular input event (Int)
    *   // Aggregate the value
    *   val sum = PerTaskState("sum", 0)
    *   sum.set(sum.get() + x)
    *   } { case Query() => // handle portal request
    *   // Reply with the latest aggregated value
    *   reply(QueryReply(PerTaskState("sum", 0).get()))
    *   }
    *   .sink()
    *   .freeze()
    *
    *   ///////////////////////////////////////////////////////////////////////
    *   // Query
    *   ///////////////////////////////////////////////////////////////////////
    *   // Atom(0), Atom(0), ...
    *   val queryTrigger = Generators.fromListOfLists(List.fill(10)(List(0)))
    *
    *   // The requesting workflow
    *   val queryWorkflow = Workflows[Int, Nothing]("queryWorkflow")
    *   .source(queryTrigger.stream)
    *   // The asker consumes a stream of 0s, for each event it will send a Query
    *   // to the portal, and wait for and print the reply.
    *   .asker[Nothing](portal) { x =>
    *   // query the replier
    *   val future: Future[QueryReply] = ask(portal)(Query())
    *   future.await { // wait for the reply to complete
    *   future.value.get match
    *   case QueryReply(x) =>
    *   // print the aggregate to log
    *   ctx.log.info(x.toString())
    *   }
    *   }
    *   .sink()
    *   .freeze()
    *
    * }
    *
    * val system = Systems.test()
    * system.launch(myApp)
    * system.stepUntilComplete()
    * system.shutdown()
    *   }}}
    */
  def Portal[T, R](name: String, f: T => Long)(using ab: ApplicationBuilder): AtomicPortalRef[T, R] =
    ab.portals.portal(name, f)

  /** Build a new PortalsApplication.
    *
    * Use this method to create a new Portals application. Define the
    * application as the second parameter to this method, using the contextual
    * application builder. Within this environment you can use the methods of
    * the contextual appllication builder, and other related methods that use
    * the contextual application builder from the [[DSL]], including
    * [[Sequencers]], [[Connections]], [[Splitters]], [[Portal]], [[Workflows]],
    * [[Generators]], [[Splits]], and [[Registry]].
    *
    * @example
    *   {{{
    * // define app
    * val myApp = PortalsApp("myApp") {
    *   val generator = Generators.signal[String]("hello world")
    *   val workflow = Workflows[String, String]()
    *     .source(generator.stream)
    *     .logger()
    *     .sink()
    *     .freeze()
    * }
    * // launch app
    * val system = Systems.test()
    * system.launch(myApp)
    * system.stepUntilComplete()
    * system.shutdown()
    *   }}}
    *
    * @param name
    *   The name of the application.
    * @param app
    *   The application factory (see example).
    * @return
    *   The Portals application.
    */
  def PortalsApp(name: String)(app: ApplicationBuilder ?=> Unit): Application =
    val builder = ApplicationBuilder(name)
    app(using builder)
    builder.build()

  //////////////////////////////////////////////////////////////////////////////
  // Portals FlowBuilder DSL
  //////////////////////////////////////////////////////////////////////////////

  class FlowBuilderAsker[T, U, CT, CU, CCU](fb: FlowBuilder[T, U, CT, CU]):
    def apply[Req, Rep](portals: AtomicPortalRefKind[Req, Rep]*)(
        f: AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit
    ): FlowBuilder[T, U, CU, CCU] =
      fb.asker[CCU, Req, Rep](portals: _*)(f)

  class FlowBuilderReplier[T, U, CT, CU, CCU](fb: FlowBuilder[T, U, CT, CU]):
    def apply[Req, Rep](portals: AtomicPortalRefKind[Req, Rep]*)(
        f1: ProcessorTaskContext[CU, CCU] ?=> CU => Unit
    )(
        f2: ReplierTaskContext[CU, CCU, Req, Rep] ?=> Req => Unit
    ): FlowBuilder[T, U, CU, CCU] =
      fb.replier[CCU, Req, Rep](portals: _*)(f1)(f2)

  extension [T, U, CT, CU](fb: FlowBuilder[T, U, CT, CU]) {
    def asker[CCU]: FlowBuilderAsker[T, U, CT, CU, CCU] = new FlowBuilderAsker[T, U, CT, CU, CCU](fb)

    def replier[CCU]: FlowBuilderReplier[T, U, CT, CU, CCU] = new FlowBuilderReplier[T, U, CT, CU, CCU](fb)
  }
end DSL // object
