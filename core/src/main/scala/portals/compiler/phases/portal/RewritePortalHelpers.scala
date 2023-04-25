package portals.compiler.phases.portal

import scala.collection.mutable

import portals.api.builder.TaskBuilder
import portals.application.*
import portals.application.sequencer.Sequencers
import portals.application.splitter.Splitters
import portals.application.task.*
import portals.compiler.phases.portal.RewritePortalEvents.RewriteEvent
import portals.runtime.WrappedEvents
import portals.util.Common.Types.*

object RewritePortalHelpers:
  class MutApplication(application: Application):
    var app: Application = application

  class MutWorkflow(workflow: Workflow[_, _]):
    var wf: Workflow[_, _] = workflow

  def taskContainsReplyers(task: GenericTask[_, _, _, _], portalPath: Path): Boolean =
    task match {
      case x @ AskerTask(_) =>
        false
      case x @ ReplierTask(_, _) =>
        x.portals.contains(AtomicPortalRef(portalPath))
      case x @ AskerReplierTask(_, _) =>
        x.replyerportals.contains(AtomicPortalRef(portalPath))
      case _ => false
    }

  def extractTasksWithAsks(workflow: Workflow[_, _]): Map[Path, GenericTask[_, _, _, _]] =
    workflow.tasks.filter { case (_, AskerTask(_)) => true; case _ => false }

  def extractTasksWithRepliers(workflow: Workflow[_, _]): Map[Path, GenericTask[_, _, _, _]] =
    workflow.tasks.filter { case (_, ReplierTask(_, _)) => true; case _ => false }

  def extractTasksWithAskerRepliers(workflow: Workflow[_, _]): Map[Path, GenericTask[_, _, _, _]] =
    workflow.tasks.filter { case (_, AskerReplierTask(_, _)) => true; case _ => false }

  def hasPortal(workflow: Workflow[_, _]): Boolean =
    extractTasksWithAsks(workflow).nonEmpty
      || extractTasksWithRepliers(workflow).nonEmpty
      || extractTasksWithAskerRepliers(workflow).nonEmpty

  def extractPortalsRefs(workflow: Workflow[_, _]): Seq[AtomicPortalRefKind[_, _]] =
    (extractTasksWithAsks(workflow)
      ++ extractTasksWithRepliers(workflow)
      ++ extractTasksWithAskerRepliers(workflow)).flatMap {
      case (_, task @ AskerReplierTask(_, _)) =>
        task.askerportals ++ task.replyerportals
      case (_, task @ AskerTask(_)) =>
        task.portals
      case (_, task @ ReplierTask(_, _)) =>
        task.portals
      case _ =>
        Seq.empty
    }.toSeq

  def extractAskingPortalRefs(workflow: Workflow[_, _]): Seq[AtomicPortalRefKind[_, _]] =
    (extractTasksWithAsks(workflow)
      ++ extractTasksWithAskerRepliers(workflow)).flatMap {
      case (_, task @ AskerReplierTask(_, _)) =>
        task.askerportals
      case (_, task @ AskerTask(_)) =>
        task.portals
      case _ =>
        Seq.empty
    }.toSeq

  def extractReplyingPortalRefs(workflow: Workflow[_, _]): Seq[AtomicPortalRefKind[_, _]] =
    (extractTasksWithRepliers(workflow)
      ++ extractTasksWithAskerRepliers(workflow)).flatMap {
      case (_, task @ AskerReplierTask(_, _)) =>
        task.replyerportals
      case (_, task @ ReplierTask(_, _)) =>
        task.portals
      case _ =>
        Seq.empty
    }.toSeq

  extension (path: Path) {
    def stream: Path = path + "/stream"
    def sequencer: Path = path + "/sequencer"
    def splitter: Path = path + "/splitter"
    def workflow: Path = path + "/workflow"
    def connection: Path = path + "/connection"
    def splits: Path = path + "/split"
    def id(id: String): Path = path + "/$" + id
    def hidden: Path = path + "/hidden"
  }

  def addSequencer(path: Path)(using mapp: MutApplication): AtomicSequencer[Any] =
    val seqStream = AtomicStream[Any](path.stream)
    val seqStreamRef = AtomicStreamRef(seqStream)
    val seq = Sequencers.random[Any]()
    val atomicSeq = AtomicSequencer[Any](path, seqStreamRef, seq)
    mapp.app = mapp.app.copy(
      streams = mapp.app.streams :+ seqStream,
      sequencers = mapp.app.sequencers :+ atomicSeq,
    )
    atomicSeq

  def addKeyByWorkflow(path: Path, consumes: AtomicStreamRef[Any], keyF: Any => Long)(using
      mapp: MutApplication
  ): Workflow[Any, Any] =
    val wfStream = AtomicStream[Any](path.stream)
    val wfStreamRef = AtomicStreamRef[Any](wfStream)
    val keyByTask = TaskBuilder.key[Any] { x =>
      x match
        case RewriteEvent(WrappedEvents.Reply(_, _, event)) =>
          keyF(event)
        case RewriteEvent(WrappedEvents.Ask(_, _, event)) =>
          keyF(event)
        case _ => ???
    }
    val wf = Workflow(
      path = path,
      consumes = consumes,
      stream = wfStreamRef,
      tasks = Map("key" -> keyByTask),
      source = "$0",
      sink = "$1",
      connections = List(("key", "$1"), ("$0", "key")),
    )
    mapp.app = mapp.app.copy(
      workflows = mapp.app.workflows :+ wf,
      streams = mapp.app.streams :+ wfStream,
    )
    wf

  def addSplitter(path: Path, consumes: AtomicStreamRef[Any])(using mapp: MutApplication): AtomicSplitter[Any] =
    val splitter = Splitters.empty[Any]()
    val atomicSplitter =
      AtomicSplitter[Any](
        path,
        consumes,
        List.empty,
        splitter
      )
    mapp.app = mapp.app.copy(
      splitters = mapp.app.splitters :+ atomicSplitter,
    )
    atomicSplitter

  def addSplit(
      path: Path,
      from: AtomicSplitterRefKind[Any],
      to: AtomicStreamRefKind[Any],
      filter: Any => Boolean
  )(using
      mapp: MutApplication
  ): AtomicSplit[Any] =
    val split = AtomicSplit[Any](
      path,
      from,
      to,
      filter,
    )
    mapp.app = mapp.app.copy(
      splits = mapp.app.splits :+ split,
    )
    split

  def replacePathWith[T <: AST](path: Path, newItem: T, list: List[T]): List[T] =
    list.map { item =>
      if item.path == path then newItem else item
    }

  def connectStreamWithSeq[T](path: Path, stream: AtomicStreamRefKind[Any], sequencerRef: AtomicSequencerRef[Any])(using
      mapp: MutApplication
  ): AtomicConnection[Any] =
    val atomicConnection = AtomicConnection(
      path.connection,
      stream,
      sequencerRef,
    )
    mapp.app = mapp.app.copy(
      connections = mapp.app.connections :+ atomicConnection,
    )
    atomicConnection

  private val _random = scala.util.Random()
  def freshId: String = _random.nextInt().toString

  def addSplitToPortalSplitter(path: Path, id: String, filter: Any => Boolean)(using
      mapp: MutApplication
  ): AtomicSplit[Any] =
    val splitterRef = AtomicSplitterRef[Any](path.splitter)
    val splitPath = path.splitter.splits.id(id)
    val splitStream = AtomicStream[Any](splitPath.stream)
    val atomicSplit = AtomicSplit[Any](
      splitPath,
      splitterRef,
      splitStream.ref,
      filter
    )
    mapp.app = mapp.app.copy(
      streams = mapp.app.streams :+ splitStream,
      splits = mapp.app.splits :+ atomicSplit,
    )
    atomicSplit

  /** GENERATED BY CHAT-GPT-3.5 */
  def topologicalSort(edges: List[(String, String)]): List[(String, String)] = {
    val graph = mutable.Map[String, List[String]]().withDefaultValue(Nil)
    val visited = mutable.Set[String]()
    val stack = mutable.Stack[String]()
    val sortedEdges = mutable.ListBuffer[(String, String)]()

    // Create graph from edges
    for ((u, v) <- edges) {
      graph(u) ::= v
    }

    // Perform DFS to obtain topological order
    def dfs(node: String): Unit = {
      visited += node
      for (neighbor <- graph(node)) {
        if (!visited.contains(neighbor)) {
          dfs(neighbor)
        }
      }
      stack.push(node)
    }
    for (u <- graph.keys) {
      if (!visited.contains(u)) {
        dfs(u)
      }
    }

    // Construct sorted edges list from topological order
    while (stack.nonEmpty) {
      val u = stack.pop()
      for (v <- graph(u)) {
        sortedEdges += ((u, v))
      }
    }
    sortedEdges.toList
  }

  def updateSource(_source: Path)(using mwf: MutWorkflow): Unit =
    mwf.wf = mwf.wf.copy(
      source = _source,
    )

  def addTask(path: Path, task: GenericTask[_, _, _, _])(using mwf: MutWorkflow): Unit =
    mwf.wf = mwf.wf.copy(
      tasks = mwf.wf.tasks + (path -> task),
    )

  def appendConnection(from: Path, to: Path)(using mwf: MutWorkflow): Unit =
    mwf.wf = mwf.wf.copy(
      connections = mwf.wf.connections :+ (from -> to),
    )
