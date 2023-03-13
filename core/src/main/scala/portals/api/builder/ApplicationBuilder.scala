package portals.api.builder

import portals.application.Application

/** Builder for Portals applications.
  *
  * The preferred way of building Portals applications is via the
  * [[portals.api.dsl.DSL.PortalsApp]], and the various methods of the
  * [[portals.api.dsl.DSL]].
  *
  * @see
  *   [[portals.api.dsl.DSL]]
  * @see
  *   [[portals.api.dsl.DSL.PortalsApp]]
  *
  * @example
  *   {{{val builder = ApplicationBuilder("my-app")}}}
  */
trait ApplicationBuilder:
  /** Build the application.
    *
    * This method will freeze and build the application. May throw an exception
    * if the application is not valid.
    *
    * @return
    *   the built application
    */
  def build(): Application

  /** Access the registry builder.
    *
    * The registry builder is used to `get` other applications, streams,
    * sequencers, and more from the registry.
    *
    * @example
    *   {{{builder.registry.streams.get[String]("path/to/stream")}}}
    *
    * @return
    *   the registry builder
    */
  def registry: RegistryBuilder

  /** Access the workflows builder.
    *
    * The workflows builder is used to create workflows.
    *
    * If no name is provided, the workflow will be given a generated unique
    * name.
    *
    * @example
    *   {{{builder.workflows[String, String].source(stream).map(_ + "!").sink().freeze()}}}
    *
    * @return
    *   the workflows builder
    */
  def workflows[T, U]: WorkflowBuilder[T, U]

  /** Access the workflows builder.
    *
    * The workflows builder is used to create workflows.
    *
    * @example
    *   {{{builder.workflows[String, String]("wfname").source(stream).map(_ + "!").sink().freeze()}}}
    *
    * @param name
    *   the name of the workflow
    * @return
    *   the workflows builder
    */
  def workflows[T, U](name: String = null): WorkflowBuilder[T, U]

  /** Access the splitter builder.
    *
    * The splitter splits an input stream into multiple output streams. A split
    * can be added to the spltiter via the `splits` builder. The split takes a
    * predicate to determine which events are filtered for the split.
    *
    * If no name is provided, the stream will be given a generated unique name.
    *
    * @example
    *   {{{builder.splitters.empty[String]}}}
    *
    * @return
    *   the streams builder
    */
  def splitters: SplitterBuilder

  /** Access the splitter builder.
    *
    * The splitter splits an input stream into multiple output streams. A split
    * can be added to the spltiter via the `splits` builder. The split takes a
    * predicate to determine which events are filtered for the split.
    *
    * @example
    *   {{{builder.splitters.empty[String]}}}
    *
    * @param name
    *   the name of the splitter
    * @return
    *   the streams builder
    */
  def splitters(name: String = null): SplitterBuilder

  /** Access the SplitBuilder to add splits to splitters.
    *
    * The split builder is used to add a split to a splitter. A split takes a
    * predicate to determine which events are filtered for the split.
    *
    * @example
    *   {{{builder.splits.split[String](_ == "foo")}}}
    *
    * @return
    *   the split builder
    */
  def splits: SplitBuilder

  /** Access the SplitBuilder to add splits to splitters.
    *
    * The split builder is used to add a split to a splitter. A split takes a
    * predicate to determine which events are filtered for the split.
    *
    * If no name is provided, the split will be given a generated unique name.
    *
    * @example
    *   {{{builder.splits("splitname").split[String](_ == "foo")}}}
    *
    * @param name
    *   the name of the split
    * @return
    *   the split builder
    */
  def splits(name: String = null): SplitBuilder

  /** GeneratorBuilder for generating streams.
    *
    * The generator builder is used to create generators, which are used to
    * generate streams. It is recommended to use the methods of the
    * [[GeneratorBuilder]] to do this.
    *
    * @example
    *   {{{builder.generators.fromList[T](List(1, 2, 3))}}} ]}}}
    *
    * @return
    *   the generator builder
    */
  def generators: GeneratorBuilder

  /** GeneratorBuilder for generating streams.
    *
    * The generator builder is used to create generators, which are used to
    * generate streams. It is recommended to use the methods of the
    * [[GeneratorBuilder]] to do this.
    *
    * If no name is provided, the generator will be given a generated unique
    *
    * @example
    *   {{{builder.generators.fromList[T](List(1, 2, 3))}}} ]}}}
    *
    * @param name
    *   the name of the generator
    * @return
    *   the generator builder
    */
  def generators(name: String = null): GeneratorBuilder

  /** Sequence multiple streams into a single sequenced stream.
    *
    * The sequencer builder creates sequencers. We can add new inputs to the
    * sequencer via the [[ConnectionBuilder]].
    *
    * If no name is provided, the sequencer will be given a generated unique
    *
    * @example
    *   {{{builder.sequencers.random[String]()}}}
    *
    * @return
    *   the sequencer builder
    */
  def sequencers: SequencerBuilder

  /** Sequence multiple streams into a single sequenced stream.
    *
    * The sequencer builder creates sequencers. We can add new inputs to the
    * sequencer via the [[ConnectionBuilder]].
    *
    * @example
    *   {{{builder.sequencers("name").random[String]("seqname")}}}
    *
    * @param name
    *   the name of the sequencer
    * @return
    *   the sequencer builder
    */
  def sequencers(name: String = null): SequencerBuilder

  /** Connect a stream to a sequencer.
    *
    * Creates a ConnectionBuilder which can be used to connect streams to
    * sequencers.
    *
    * @example
    *   {{{builder.connections.connect(stream, sequencer)}}}
    *
    * @return
    *   the connection builder
    */
  def connections: ConnectionBuilder

  /** Connect a stream to a sequencer.
    *
    * Creates a ConnectionBuilder which can be used to connect streams to
    * sequencers.
    *
    * @example
    *   {{{builder.connections("name").connect(stream, sequencer)}}}
    *
    * @param name
    *   the name of the connection
    * @return
    *   the connection builder
    */
  def connections(name: String = null): ConnectionBuilder

  /** Build Portals.
    *
    * The PortalBuilder is used to build portals. A workflow can connect to a
    * portal via either an `asker` task or a `replier` task.
    *
    * If no name is provided, the portal will be given a generated unique name.
    *
    * @example
    *   {{{builder.portals.portal[String, Int]()}}}
    *
    * @return
    *   the portal builder
    */
  def portals: PortalBuilder

  /** Build Portals.
    *
    * The PortalBuilder is used to build portals. A workflow can connect to a
    * portal via either an `asker` task or a `replier` task.
    *
    * @example
    *   {{{builder.portals("name").portal[String, Int]()}}}
    *
    * @param name
    *   the name of the portal
    * @return
    *   the portal builder
    */
  def portals(name: String = null): PortalBuilder
end ApplicationBuilder // trait

object ApplicationBuilder:
  /** Create a new ApplicationBuilder for building Portals applications.
    *
    * @example
    *   {{{val builder = ApplicationBuilder("myapp")}}}
    *
    * @param name
    *   the name of the application
    * @return
    *   the application builder
    */
  def apply(name: String): ApplicationBuilder =
    val _path = "/" + name
    given ApplicationBuilderContext = ApplicationBuilderContext(_path = _path)
    new ApplicationBuilderImpl()
