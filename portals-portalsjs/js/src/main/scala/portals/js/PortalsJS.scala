package portals.js

import scalajs.js.annotation.JSExport
import scalajs.js.annotation.JSExportAll
import scalajs.js.annotation.JSExportStatic
import scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("PortalsJS")
object PortalsJS:
  //////////////////////////////////////////////////////////////////////////////
  // Imports
  //////////////////////////////////////////////////////////////////////////////

  import scala.annotation.targetName
  import scalajs.js.Array
  import scalajs.js.BigInt
  import scalajs.js.Function1
  import scalajs.js.Function2
  import scalajs.js.Iterator

  import portals.api.builder.*
  import portals.api.builder.TaskExtensions.*
  import portals.api.dsl.DSL
  import portals.api.dsl.DSL.*
  import portals.api.dsl.ExperimentalDSL.*
  import portals.application.*
  import portals.application.task.*
  import portals.util.Future
  import portals.util.Key

  //////////////////////////////////////////////////////////////////////////////
  // Types
  //////////////////////////////////////////////////////////////////////////////

  object Types:

    extension [T](array: Array[T]) {
      def toScala: List[T] = array.toList
    }

    extension (bigInt: BigInt) {
      def toScala: scala.BigInt = scala.BigInt(bigInt.toString)
    }

    extension [T](iterator: Iterator[T]) {
      def toScala: scala.Iterator[T] = iterator.toIterator
    }

    opaque type Function1JS[T, U] = Function1[T, U]
    extension [T, U](f: Function1JS[T, U]) {
      @targetName("toScalaF1")
      inline def toScala: T => U = f
    }

    opaque type Function2JS[T, U, V] = Function1[T, Function1[U, V]]
    extension [T, U, V](f: Function2JS[T, U, V]) {
      @targetName("toScalaF2")
      inline def toScala: T => U => V = t => u => f(t)(u)
    }

    opaque type ContextFunction1JS[T, U] = Function1[T, U]
    extension [T, U](f: ContextFunction1JS[T, U]) {
      @targetName("toScalaCF1")
      inline def toScala: T ?=> U = t ?=> f(t)
    }

    opaque type ContextFunction2JS[T, U, V] = Function1[T, Function1[U, V]]
    extension [T, U, V](f: ContextFunction2JS[T, U, V]) {
      @targetName("toScalaCF2")
      inline def toScala: T ?=> U => V = t ?=> u => f(t)(u)
    }

    type WrappedType[C, T, U] = Function1[C, Function1[T, U]]
    type WithWrapperF[C, T, U] = Function1[C, Function1[WrappedType[C, T, U], Function1[T, U]]]

    object WithWrapperF {
      def toScala[C, T, U](f: WithWrapperF[C, T, U]): C ?=> (C ?=> T => U) => T => U = c ?=>
        w => t => f(c)(cc ?=> tt => w(tt))(t)
    }

  //////////////////////////////////////////////////////////////////////////////
  // Registry
  //////////////////////////////////////////////////////////////////////////////

  // TODO: decide if we should bring back the registry in the future?
  // @JSExportAll
  // class AppRegistryJS(system: portals.system.TestSystem):
  //   import scalajs.js.JSConverters._
  //   def show: String =
  //     scalajs.js.JSON.stringify(this.all, space = 2)
  //   def all: scalajs.js.Dictionary[Array[String]] =
  //     scalajs.js.Dictionary(
  //       "applications" -> this.applications,
  //       "streams" -> this.streams,
  //       "portals" -> this.portals,
  //       "workflows" -> this.workflows,
  //       "sequencers" -> this.sequencers,
  //       "splitters" -> this.splitters,
  //       "generators" -> this.generators,
  //       "connections" -> this.connections
  //     )
  //   def applications: Array[String] =
  //     system.registry.applications.map(_._2.path).toJSArray
  //   def streams: Array[String] =
  //     system.registry.streams.map(_._2.stream.path).toJSArray
  //   def portals: Array[String] =
  //     system.registry.portals.map(_._2.portal.path).toJSArray
  //   def workflows: Array[String] =
  //     system.registry.workflows.map(_._2.wf.path).toJSArray
  //   def sequencers: Array[String] =
  //     system.registry.sequencers.map(_._2.sequencer.path).toJSArray
  //   def splitters: Array[String] =
  //     system.registry.splitters.map(_._2.splitter.path).toJSArray
  //   def generators: Array[String] =
  //     system.registry.generators.map(_._2.generator.path).toJSArray
  //   def connections: Array[String] =
  //     system.registry.connections.map(_._2.connection.path).toJSArray

  //////////////////////////////////////////////////////////////////////////////
  // System
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class SystemJS():
    private val system = portals.system.Systems.test()
    // def registry: AppRegistryJS = new AppRegistryJS(system)
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
    // TODO: the with name option should be unified with the scala portals API.
    def workflows[T, U]: WorkflowBuilderJS[T, U] = builder.workflows[T, U].toJS
    def workflowsWithName[T, U](name: String): WorkflowBuilderJS[T, U] = builder.workflows[T, U](name).toJS
    def splitters: SplitterBuilderJS = builder.splitters.toJS
    def splittersWithName(name: String): SplitterBuilderJS = builder.splitters(name).toJS
    def splits: SplitBuilderJS = builder.splits.toJS
    def splitsWithName(name: String): SplitBuilderJS = builder.splits(name).toJS
    def generators: GeneratorBuilderJS = builder.generators.toJS
    def generatorsWithName(name: String): GeneratorBuilderJS = builder.generators(name).toJS
    def sequencers: SequencerBuilderJS = builder.sequencers.toJS
    def sequencersWithName(name: String): SequencerBuilderJS = builder.sequencers(name).toJS
    def connections: ConnectionBuilderJS = builder.connections.toJS
    def connectionsWithName(name: String): ConnectionBuilderJS = builder.connections(name).toJS
    def portal: PortalBuilderJS = builder.portals.toJS
    def tasks: TaskBuilderJS = TaskBuilder.toJS

  @JSExport
  def ApplicationBuilder(name: String): ApplicationBuilderJS = ApplicationBuilderJS(name)

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

    // deprecated in favor of union
    // def combineAllFrom[CU, CCU](
    //     others: Array[FlowBuilderJS[T, U, _, CU]],
    //     task: GenericTask[CU, CCU, _, _]
    // ): FlowBuilderJS[T, U, CU, CCU] =
    //   fb.combineAllFrom(others.toScala.map(_.fb): _*)(task).toJS

    def map[CCU](f: ContextFunction2JS[MapTaskContext[CU, CCU], CU, CCU]): FlowBuilderJS[T, U, CU, CCU] =
      fb.map(f.toScala).toJS

    def key(f: Function1JS[CU, BigInt]): FlowBuilderJS[T, U, CU, CU] =
      fb.key(x => f.toScala(x).toScala.toLong).toJS

    def task[CCU](taskBehavior: GenericTask[CU, CCU, _, _]): FlowBuilderJS[T, U, CU, CCU] =
      fb.task(taskBehavior).toJS

    def processor[CCU](f: ContextFunction2JS[ProcessorTaskContext[CU, CCU], CU, Unit]): FlowBuilderJS[T, U, CU, CCU] =
      fb.processor(f.toScala).toJS

    def flatMap[CCU](f: ContextFunction2JS[MapTaskContext[CU, CCU], CU, Array[CCU]]): FlowBuilderJS[T, U, CU, CCU] =
      fb.flatMap((c: MapTaskContext[CU, CCU]) ?=> u => f.toScala(using c)(u).toScala).toJS

    def filter(f: Function1JS[CU, Boolean]): FlowBuilderJS[T, U, CU, CU] =
      fb.filter(f.toScala).toJS

    def vsm[CCU](defaultTask: VSMTask[CU, CCU]): FlowBuilderJS[T, U, CU, CCU] =
      fb.vsm(defaultTask).toJS

    def init[CCU](
        initFactory: ContextFunction1JS[ProcessorTaskContext[CU, CCU], GenericTask[CU, CCU, Nothing, Nothing]]
    ): FlowBuilderJS[T, U, CU, CCU] =
      fb.init(initFactory.toScala).toJS

    def identity(): FlowBuilderJS[T, U, CU, CU] =
      fb.identity().toJS

    def logger(prefix: String = ""): FlowBuilderJS[T, U, CU, CU] =
      fb.logger(prefix).toJS

    // unnecessary in JS
    // def checkExpectedType[CCU >: CU <: CU](): FlowBuilderJS[T, U, CT, CU] =
    //   fb.checkExpectedType().toJS

    def withName(name: String): FlowBuilderJS[T, U, CT, CU] =
      fb.withName(name).toJS

    def withOnNext(onNext: ContextFunction2JS[ProcessorTaskContext[CT, CU], CT, Unit]): FlowBuilderJS[T, U, CT, CU] =
      fb.withOnNext(onNext.toScala).toJS

    def withOnError(
        onError: ContextFunction2JS[ProcessorTaskContext[CT, CU], Throwable, Unit]
    ): FlowBuilderJS[T, U, CT, CU] =
      fb.withOnError(onError.toScala).toJS

    def withOnComplete(
        onComplete: ContextFunction1JS[ProcessorTaskContext[CT, CU], Unit]
    ): FlowBuilderJS[T, U, CT, CU] =
      fb.withOnComplete(onComplete.toScala).toJS

    def withOnAtomComplete(
        onAtomComplete: ContextFunction1JS[ProcessorTaskContext[CT, CU], Unit]
    ): FlowBuilderJS[T, U, CT, CU] =
      fb.withOnAtomComplete(onAtomComplete.toScala).toJS

    def withWrapper(
        f: WithWrapperF[ProcessorTaskContext[CT, CU], CT, Unit]
    ): FlowBuilderJS[T, U, CT, CU] =
      fb.withWrapper(WithWrapperF.toScala(f)).toJS

    def withStep(task: GenericTask[CT, CU, Nothing, Nothing]): FlowBuilderJS[T, U, CT, CU] =
      fb.withStep(task).toJS

    def withLoop(count: Int)(task: GenericTask[CT, CU, Nothing, Nothing]): FlowBuilderJS[T, U, CT, CU] =
      fb.withLoop(count)(task).toJS

    def withAndThen[CCU](task: GenericTask[CU, CCU, Nothing, Nothing]): FlowBuilderJS[T, U, CT, CCU] =
      fb.withAndThen(task).toJS

    def allWithOnAtomComplete[WT, WU](
        onAtomComplete: ContextFunction1JS[ProcessorTaskContext[CT, CU], Unit]
    ): FlowBuilderJS[T, U, CT, CU] =
      fb.allWithOnAtomComplete(onAtomComplete.toScala).toJS

    def allWithWrapper[WT, WU](
        f: WithWrapperF[ProcessorTaskContext[WT, WU], WT, Unit]
    ): FlowBuilderJS[T | WT, U | WU, CT, CU] =
      fb.allWithWrapper(WithWrapperF.toScala(f)).toJS

    def asker[CCU, Req, Rep](
        portals: AtomicPortalRefKind[Req, Rep]
    )(
        f: ContextFunction2JS[TaskContextJS[CU, CCU, Req, Rep], CU, Unit]
    ): FlowBuilderJS[T, U, CU, CCU] =
      // TODO: fix this to make it more uniform with the rest (see comment below on TaskContext)
      fb.asker[CCU, Req, Rep](portals)(c ?=> f.toScala(using c.toJS)).toJS

    def replier[CCU, Req, Rep](
        portals: AtomicPortalRefKind[Req, Rep]
    )(
        f1: ContextFunction2JS[TaskContextJS[CU, CCU, Req, Rep], CU, Unit]
    )(
        f2: ContextFunction2JS[TaskContextJS[CU, CCU, Req, Rep], Req, Unit]
    ): FlowBuilderJS[T, U, CU, CCU] =
      // TODO: fix this to make it more uniform with the rest (see comment below on TaskContext)
      fb.replier[CCU, Req, Rep](portals)(c ?=> f1.toScala(using c.toJS))(c ?=> f2.toScala(using c.toJS)).toJS

    def askerreplier[CCU, Req, Rep](
        askerportals: AtomicPortalRefKind[Req, Rep]
    )(
        replierportals: AtomicPortalRefKind[Req, Rep]
    )(
        f1: ContextFunction2JS[TaskContextJS[CU, CCU, Req, Rep], CU, Unit]
    )(
        f2: ContextFunction2JS[TaskContextJS[CU, CCU, Req, Rep], Req, Unit]
    ): FlowBuilderJS[T, U, CU, CCU] =
      // TODO: make it look nicer
      fb.askerreplier[CCU, Req, Rep](askerportals)(replierportals)(c ?=> f1.toScala(using c.toJS))(c ?=>
        f2.toScala(using c.toJS)
      ).toJS

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
    def fromIterator[T](it: Iterator[T]): AtomicGeneratorRef[T] =
      gb.fromIterator(it.toScala)
    def fromIterator[T](it: Iterator[T], keys: Iterator[Key]): AtomicGeneratorRef[T] =
      gb.fromIterator(it.toScala, keys.toScala)
    def fromIteratorOfIterators[T](itit: Iterator[Iterator[T]]): AtomicGeneratorRef[T] =
      gb.fromIteratorOfIterators(itit.toScala.map(_.toScala))
    def fromIteratorOfIterators[T](
        itit: Iterator[Iterator[T]],
        keys: Iterator[Iterator[Key]]
    ): AtomicGeneratorRef[T] =
      gb.fromIteratorOfIterators(itit.toScala.map(_.toScala), keys.toScala.map(_.toScala))
    def fromArray[T](array: Array[T]): AtomicGeneratorRef[T] =
      gb.fromList(array.toScala)
    def fromArray[T](array: Array[T], keys: Array[Key]): AtomicGeneratorRef[T] =
      gb.fromList(array.toScala, keys.toScala)
    def fromArrayOfArrays[T](arrayarray: Array[Array[T]]): AtomicGeneratorRef[T] =
      gb.fromListOfLists(arrayarray.toScala.map(_.toScala))
    def fromArrayOfArrays[T](arrayarray: Array[Array[T]], keys: Array[Array[Key]]): AtomicGeneratorRef[T] =
      gb.fromListOfLists(arrayarray.toScala.map(_.toScala), keys.toScala.map(_.toScala))
    def fromRange(start: Int, end: Int, step: Int): AtomicGeneratorRef[Int] =
      gb.fromRange(start, end, step)
    def signal[T](value: T): AtomicGeneratorRef[T] =
      gb.signal(value)
    def empty[T](): AtomicGeneratorRef[T] =
      gb.empty

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
  // Split Builder
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class SplitBuilderJS(sb: SplitBuilder):
    import Types.*
    def split[T](splitter: AtomicSplitterRefKind[T], f: Function1JS[T, Boolean]): AtomicStreamRef[T] =
      sb.split(splitter, f.toScala)

  extension (sb: SplitBuilder) {
    def toJS: SplitBuilderJS = SplitBuilderJS(sb)
  }
  //////////////////////////////////////////////////////////////////////////////
  // Portal Builder
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class PortalBuilderJS(pb: PortalBuilder):
    import Types.*

    def portal[T, R](name: String): AtomicPortalRef[T, R] =
      pb.portal(name)

    def portal[T, R](name: String, f: Function1JS[T, BigInt]): AtomicPortalRef[T, R] =
      pb.portal(name, x => f.toScala(x).toScala.toLong)

  extension (pb: PortalBuilder) {
    def toJS: PortalBuilderJS = PortalBuilderJS(pb)
  }

  //////////////////////////////////////////////////////////////////////////////
  // Task Builder
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class TaskBuilderJS():
    import Types.*

    def processor[T, U](
        onNext: ContextFunction2JS[ProcessorTaskContext[T, U], T, Unit]
    ): GenericTask[T, U, Nothing, Nothing] =
      TaskBuilder.processor(onNext.toScala)

    def identity[T]: GenericTask[T, T, _, _] =
      TaskBuilder.identity[T]

    def map[T, U](f: ContextFunction2JS[MapTaskContext[T, U], T, U]): GenericTask[T, U, Nothing, Nothing] =
      TaskBuilder.map(f.toScala)

    def flatMap[T, U](
        f: ContextFunction2JS[MapTaskContext[T, U], T, Array[U]]
    ): GenericTask[T, U, Nothing, Nothing] =
      TaskBuilder.flatMap(ctx ?=> x => f.toScala(using ctx)(x).toScala)

    def filter[T](f: Function1JS[T, Boolean]): GenericTask[T, T, Nothing, Nothing] =
      TaskBuilder.filter(f.toScala)

    def key[T](f: Function1JS[T, Long]): GenericTask[T, T, Nothing, Nothing] =
      TaskBuilder.key(f.toScala)

  extension (tb: TaskBuilder.type) {
    def toJS: TaskBuilderJS = TaskBuilderJS()
  }

  //////////////////////////////////////////////////////////////////////////////
  // Task State Extension
  //////////////////////////////////////////////////////////////////////////////

  @JSExportAll
  class PerTaskStateJS[T](name: String, initValue: T, ctx: StatefulTaskContext):
    val state = portals.application.task.PerTaskState(name, initValue)
    def get(): T = state.get()(using ctx)
    def set(value: T): Unit = state.set(value)(using ctx)
    def del(): Unit = state.del()(using ctx)

  @JSExport
  def PerTaskState[T](name: String, initValue: T, ctx: StatefulTaskContext): PerTaskStateJS[T] =
    PerTaskStateJS[T](name, initValue, ctx)

  @JSExportAll
  class PerKeyStateJS[T](name: String, initValue: T, ctx: StatefulTaskContext):
    val state = portals.application.task.PerKeyState(name, initValue)
    def get(): T = state.get()(using ctx)
    def set(value: T): Unit = state.set(value)(using ctx)
    def del(): Unit = state.del()(using ctx)

  @JSExport
  def PerKeyState[T](name: String, initValue: T, ctx: StatefulTaskContext): PerKeyStateJS[T] =
    PerKeyStateJS[T](name, initValue, ctx)

  //////////////////////////////////////////////////////////////////////////////
  // Task Context
  //////////////////////////////////////////////////////////////////////////////

  // TODO: Internal API. Used for constructing a JS API safe TaskContext object
  // within the Asker for now. To be used in replacement for all TaskContext
  // within the JS API at a later time.
  @JSExportAll
  class TaskContextJS[T, U, V, W](ctx: TaskContextImpl[T, U, V, W]) extends StatefulTaskContext:
    private[portals] val _inner_ctx = ctx
    import Types.*
    def state = ctx.state
    def emit(event: U) = ctx.emit(event)
    def log = ctx.log
    def ask(portal: AtomicPortalRefKind[V, W], msg: V) = ctx.ask(portal)(msg).toJS
    def await(future: FutureJS[W], f: Function1JS[TaskContextJS[T, U, V, W], Unit]) =
      ctx.await(future._inner_f)(c ?=> f.toScala(c.toJS))
    def reply(msg: W) = ctx.reply(msg)

  extension (ctx: GenericGenericTaskContext) {
    def toJS[T, U, V, W]: TaskContextJS[T, U, V, W] =
      TaskContextJS[T, U, V, W](ctx.asInstanceOf[TaskContextImpl[T, U, V, W]])
  }

  //////////////////////////////////////////////////////////////////////////////
  // Future
  //////////////////////////////////////////////////////////////////////////////

  // TODO: Internal API. Make it consistent with the rest.
  @JSExportAll
  class FutureJS[T](f: Future[T]):
    private[portals] val _inner_f = f
    private[portals] val id: Int = f.id
    def value(ctx: TaskContextJS[_, _, _, T]): T = f.value(using ctx._inner_ctx).get
    override def toString(): String = "Future(id=" + id + ")"

  extension [T](f: Future[T]) {
    def toJS: FutureJS[T] = FutureJS[T](f)
  }

end PortalsJS
