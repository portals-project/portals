package portals.js

import scala.scalajs.js.annotation.JSExportAll
import scala.scalajs.js.annotation.JSExportStatic
import scalajs.js.annotation.JSExport
import scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("PortalsJS")
object PortalsJS:
  //////////////////////////////////////////////////////////////////////////////
  // Imports
  //////////////////////////////////////////////////////////////////////////////

  import scala.annotation.targetName
  import scalajs.js.Array
  import scalajs.js.Function1
  import scalajs.js.Function2

  import portals.api.builder.*
  import portals.api.dsl.DSL.*
  import portals.application.*
  import portals.application.task.*

  //////////////////////////////////////////////////////////////////////////////
  // Exported Conversions
  //////////////////////////////////////////////////////////////////////////////

  @JSExport
  def List[T](x: T*): List[T] = x.toList

  @JSExport
  def Iterator[T](x: scalajs.js.Iterable[T]): Iterator[T] = x.iterator

  @JSExport
  def UDF[T, U](f: Function1[T, U]): T => U = f

  @JSExport
  def UDFC[C, T, U](f: Function1[C, Function1[T, U]]): C => T => U = c => t => f(c)(t)

  @JSExport
  def UDFWithContext[C, T, U](f: Function1[C, Function1[T, U]]): C => T => U = c => t => f(c)(t)

  //////////////////////////////////////////////////////////////////////////////
  // Types
  //////////////////////////////////////////////////////////////////////////////

  object Types:
    extension [T](array: Array[T]) {
      def toScala: List[T] = array.toList
    }

    type ShortUDFWithContextJS[C, T] <: Function1[C, T]
    extension [C, T](f: ShortUDFWithContextJS[C, T]) {
      @targetName("toScalaAlt")
      def toScala: C ?=> T = c ?=> { f(c) }
    }

    type UDFWithoutContextTypeJS[T, U] <: Function1[T, U]
    extension [T, U](f: UDFWithoutContextTypeJS[T, U]) {
      def toScala: T => U = f
    }

    type UDFWithContextTypeJS[C, T, U] <: Function1[C, Function1[T, U]]
    extension [C, T, U](f: UDFWithContextTypeJS[C, T, U]) {
      def toScala: C ?=> T => U = c ?=> t => f(c)(t)
    }

  //////////////////////////////////////////////////////////////////////////////
  // Registry
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class AppRegistryJS(system: portals.system.InterpreterSystem):
    import scalajs.js.JSConverters._
    def show: String =
      scalajs.js.JSON.stringify(this.all, space = 2)
    def all: scalajs.js.Dictionary[Array[String]] =
      scalajs.js.Dictionary(
        "applications" -> this.applications,
        "streams" -> this.streams,
        "portals" -> this.portals,
        "workflows" -> this.workflows,
        "sequencers" -> this.sequencers,
        "splitters" -> this.splitters,
        "generators" -> this.generators,
        "connections" -> this.connections
      )
    def applications: Array[String] =
      system.registry.applications.map(_._2.path).toJSArray
    def streams: Array[String] =
      system.registry.streams.map(_._2.stream.path).toJSArray
    def portals: Array[String] =
      system.registry.portals.map(_._2.portal.path).toJSArray
    def workflows: Array[String] =
      system.registry.workflows.map(_._2.wf.path).toJSArray
    def sequencers: Array[String] =
      system.registry.sequencers.map(_._2.sequencer.path).toJSArray
    def splitters: Array[String] =
      system.registry.splitters.map(_._2.splitter.path).toJSArray
    def generators: Array[String] =
      system.registry.generators.map(_._2.generator.path).toJSArray
    def connections: Array[String] =
      system.registry.connections.map(_._2.connection.path).toJSArray

  //////////////////////////////////////////////////////////////////////////////
  // System
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class SystemJS():
    private val system = portals.system.Systems.interpreter()
    def registry: AppRegistryJS = new AppRegistryJS(system)
    def launch(app: Application): Unit = system.launch(app)
    def step(): Unit = system.step()
    def stepUntilComplete(): Unit = system.stepUntilComplete()
    def shutdown(): Unit = system.shutdown()

  @JSExport
  def System(): SystemJS = SystemJS()

  //////////////////////////////////////////////////////////////////////////////
  // Application Builder
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class ApplicationBuilderJS(name: String):
    private val builder = portals.api.builder.ApplicationBuilder(name)
    def build(): Application = builder.build()
    def registry: RegistryBuilderJS = builder.registry.toJS
    def workflows[T, U]: WorkflowBuilderJS[T, U] = builder.workflows[T, U].toJS
    def generators: GeneratorBuilderJS = builder.generators.toJS
    def sequencers: SequencerBuilderJS = builder.sequencers.toJS
    def splitters: SplitterBuilderJS = builder.splitters.toJS
    def connections: ConnectionBuilderJS = builder.connections.toJS
    @JSExport("portals")
    def portal: PortalBuilderJS = builder.portals.toJS

  @JSExport
  def ApplicationBuilder(name: String): ApplicationBuilderJS = ApplicationBuilderJS(name)

  @JSExport
  def PortalsApp(name: String)(app: ApplicationBuilderJS ?=> Unit): Application =
    val builder = ApplicationBuilder(name)
    app(using builder)
    builder.build()

  //////////////////////////////////////////////////////////////////////////////
  // Flow Builder
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class FlowBuilderJS[T, U, CT, CU](val fb: FlowBuilder[T, U, CT, CU]):
    import Types.*

    def freeze(): Workflow[T, U] =
      fb.freeze()

    def sink(): FlowBuilderJS[T, U, U, U] =
      fb.asInstanceOf[FlowBuilder[T, U, U, U]].sink().toJS

    def union(others: Array[FlowBuilderJS[T, U, _, CU]]): FlowBuilderJS[T, U, CU, CU] =
      fb.union(others.toScala.map(_.fb)).toJS

    def combineAllFrom[CU, CCU](
        others: Array[FlowBuilderJS[T, U, _, CU]],
        task: GenericTask[CU, CCU, _, _]
    ): FlowBuilderJS[T, U, CU, CCU] =
      fb.combineAllFrom(others.toScala.map(_.fb): _*)(task).toJS

    def map[CCU](f: UDFWithContextTypeJS[MapTaskContext[CU, CCU], CU, CCU]): FlowBuilderJS[T, U, CU, CCU] =
      fb.map(f.toScala).toJS

    def key(f: UDFWithoutContextTypeJS[CU, Long]): FlowBuilderJS[T, U, CU, CU] =
      fb.key(f.toScala).toJS

    def task[CCU](taskBehavior: GenericTask[CU, CCU, _, _]): FlowBuilderJS[T, U, CU, CCU] =
      fb.task(taskBehavior).toJS

    def processor[CCU](f: UDFWithContextTypeJS[ProcessorTaskContext[CU, CCU], CU, Unit]): FlowBuilderJS[T, U, CU, CCU] =
      fb.processor(f.toScala).toJS

    def flatMap[CCU](
        // TODO: not compatible type Seq with JS Array
        f: UDFWithContextTypeJS[MapTaskContext[CU, CCU], CU, Seq[CCU]]
    ): FlowBuilderJS[T, U, CU, CCU] =
      fb.flatMap(f.toScala).toJS

    def filter(f: UDFWithoutContextTypeJS[CU, Boolean]): FlowBuilderJS[T, U, CU, CU] =
      fb.filter(f.toScala).toJS

    def vsm[CCU](defaultTask: VSMTask[CU, CCU]): FlowBuilderJS[T, U, CU, CCU] =
      fb.vsm(defaultTask).toJS

    def init[CCU](
        initFactory: ShortUDFWithContextJS[ProcessorTaskContext[CU, CCU], GenericTask[CU, CCU, Nothing, Nothing]]
    ): FlowBuilderJS[T, U, CU, CCU] =
      fb.init(initFactory.toScala).toJS

    def identity(): FlowBuilderJS[T, U, CU, CU] =
      fb.identity().toJS

    def logger(prefix: String = ""): FlowBuilderJS[T, U, CU, CU] =
      fb.logger(prefix).toJS

    def withName(name: String): FlowBuilderJS[T, U, CT, CU] =
      fb.withName(name).toJS

    def withOnNext(onNext: UDFWithContextTypeJS[ProcessorTaskContext[CT, CU], CT, Unit]): FlowBuilderJS[T, U, CT, CU] =
      fb.withOnNext(onNext.toScala).toJS

    def withOnError(
        onError: UDFWithContextTypeJS[ProcessorTaskContext[CT, CU], Throwable, Unit]
    ): FlowBuilderJS[T, U, CT, CU] =
      fb.withOnError(onError.toScala).toJS

    def withOnComplete(
        onComplete: ShortUDFWithContextJS[ProcessorTaskContext[CT, CU], Unit]
    ): FlowBuilderJS[T, U, CT, CU] =
      fb.withOnComplete(onComplete.toScala).toJS

    def withOnAtomComplete(
        onAtomComplete: ShortUDFWithContextJS[ProcessorTaskContext[CT, CU], Unit]
    ): FlowBuilderJS[T, U, CT, CU] =
      fb.withOnAtomComplete(onAtomComplete.toScala).toJS

    // def withWrapper

    // ...
  end FlowBuilderJS

  extension [T, U, CT, CU](fb: portals.api.builder.FlowBuilder[T, U, CT, CU]) {
    def toJS: FlowBuilderJS[T, U, CT, CU] = FlowBuilderJS(fb)
  }

  //////////////////////////////////////////////////////////////////////////////
  // Workflow Builder
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class WorkflowBuilderJS[T, U](val wf: WorkflowBuilder[T, U]):
    import Types.*
    def freeze(): Workflow[T, U] = wf.freeze()
    def source[TT >: T <: T](ref: AtomicStreamRefKind[T]): FlowBuilderJS[T, U, TT, TT] = FlowBuilderJS(wf.source(ref))

  extension [T, U](wf: portals.api.builder.WorkflowBuilder[T, U]) {
    def toJS: WorkflowBuilderJS[T, U] = WorkflowBuilderJS(wf)
  }

  //////////////////////////////////////////////////////////////////////////////
  // Registry Builder
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class RegistryBuilderJS(rb: RegistryBuilder):
    def sequencers: Registry[ExtAtomicSequencerRef] =
      rb.sequencers
    def splitters: Registry[ExtAtomicSplitterRef] =
      rb.splitters
    def streams: Registry[ExtAtomicStreamRef] =
      rb.streams
    def portals: Registry2[ExtAtomicPortalRef] =
      rb.portals

  extension (rb: RegistryBuilder) {
    def toJS: RegistryBuilderJS = RegistryBuilderJS(rb)
  }

  //////////////////////////////////////////////////////////////////////////////
  // Generator Builder
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class GeneratorBuilderJS(gb: GeneratorBuilder):
    import Types.*
    // def fromIterator[T](it: Iterator[T]): AtomicGeneratorRef[T]
    // def fromIterator[T](it: Iterator[T], keys: Iterator[Key[Long]]): AtomicGeneratorRef[T]
    // def fromIteratorOfIterators[T](itit: Iterator[Iterator[T]]): AtomicGeneratorRef[T]
    // def fromIteratorOfIterators[T](
    //     itit: Iterator[Iterator[T]],
    //     keys: Iterator[Iterator[Key[Long]]]
    // ): AtomicGeneratorRef[T]
    def fromArray[T](array: Array[T]): AtomicGeneratorRef[T] =
      gb.fromList(array.toScala)
    // def fromList[T](list: List[T], keys: List[Key[Long]]): AtomicGeneratorRef[T]
    def fromArrayOfArrays[T](arrayarray: Array[Array[T]]): AtomicGeneratorRef[T] =
      gb.fromListOfLists(arrayarray.toScala.map(_.toScala))
    // def fromListOfLists[T](listlist: List[List[T]], keys: List[List[Key[Long]]]): AtomicGeneratorRef[T]
    def fromRange(start: Int, end: Int, step: Int): AtomicGeneratorRef[Int] =
      gb.fromRange(start, end, step)
    // private[portals] def generator[T](g: Generator[T]): AtomicGeneratorRef[T]

  extension (gb: GeneratorBuilder) {
    def toJS: GeneratorBuilderJS = GeneratorBuilderJS(gb)
  }

  //////////////////////////////////////////////////////////////////////////////
  // Sequencer Builder
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class SequencerBuilderJS(sb: SequencerBuilder):
    def random[T](): AtomicSequencerRef[T] =
      sb.random()

  extension (sb: SequencerBuilder) {
    def toJS: SequencerBuilderJS = SequencerBuilderJS(sb)
  }

  //////////////////////////////////////////////////////////////////////////////
  // Connection Builder
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class ConnectionBuilderJS(cb: ConnectionBuilder):
    def connect[T](from: AtomicStreamRefKind[T], to: AtomicSequencerRefKind[T]): Unit =
      cb.connect(from, to)

  extension (sb: ConnectionBuilder) {
    def toJS: ConnectionBuilderJS = ConnectionBuilderJS(sb)
  }

  //////////////////////////////////////////////////////////////////////////////
  // Splitter Builder
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class SplitterBuilderJS(sb: SplitterBuilder):
    def empty[T](stream: AtomicStreamRefKind[T]): AtomicSplitterRef[T] =
      sb.empty(stream)

  extension (sb: SplitterBuilder) {
    def toJS: SplitterBuilderJS = SplitterBuilderJS(sb)
  }

  //////////////////////////////////////////////////////////////////////////////
  // Portal Builder
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class PortalBuilderJS(pb: PortalBuilder):
    import Types.*

    def portal[T, R](name: String): AtomicPortalRef[T, R] =
      pb.portal(name)

    def portal[T, R](name: String, f: UDFWithoutContextTypeJS[T, Long]): AtomicPortalRef[T, R] =
      pb.portal(name, f.toScala)

  extension (pb: PortalBuilder) {
    def toJS: PortalBuilderJS = PortalBuilderJS(pb)
  }
