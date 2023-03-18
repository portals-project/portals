package portals.runtime.interpreter.trackers

import scala.util.Random

import portals.application.*
import portals.application.task.AskerReplierTask
import portals.application.task.AskerTask
import portals.application.task.ReplierTask
import portals.compiler.phases.RuntimeCompilerPhases
import portals.runtime.interpreter.processors.*
import portals.runtime.BatchedEvents.*
import portals.runtime.PortalsRuntime
import portals.runtime.WrappedEvents.*

/** Internal API. Holds runtime information of the executed applications. */
private[portals] class ApplicationTracker():
  private var _applications: Map[String, Application] = Map.empty
  private var _streams: Map[String, InterpreterStream] = Map.empty
  private var _portals: Map[String, InterpreterPortal] = Map.empty
  private var _workflows: Map[String, InterpreterWorkflow] = Map.empty
  private var _sequencers: Map[String, InterpreterSequencer] = Map.empty
  private var _splitters: Map[String, InterpreterSplitter] = Map.empty
  private var _generators: Map[String, InterpreterGenerator] = Map.empty
  private var _connections: Map[String, InterpreterConnection] = Map.empty
  def applications: Map[String, Application] = _applications
  def streams: Map[String, InterpreterStream] = _streams
  def portals: Map[String, InterpreterPortal] = _portals
  def workflows: Map[String, InterpreterWorkflow] = _workflows
  def sequencers: Map[String, InterpreterSequencer] = _sequencers
  def splitters: Map[String, InterpreterSplitter] = _splitters
  def generators: Map[String, InterpreterGenerator] = _generators
  def connections: Map[String, InterpreterConnection] = _connections
  def addApplication(application: Application): Unit = _applications += application.path -> application
  def addStream(stream: AtomicStream[_]): Unit = _streams += stream.path -> InterpreterStream(stream)
  def addPortal(portal: AtomicPortal[_, _]): Unit = _portals += portal.path -> InterpreterPortal(portal)
  def addWorkflow(wf: Workflow[_, _]): Unit = _workflows += wf.path -> InterpreterWorkflow(wf)
  def addSequencer(seqr: AtomicSequencer[_]): Unit = _sequencers += seqr.path -> InterpreterSequencer(seqr)
  def addSplitter(spltr: AtomicSplitter[_]): Unit = _splitters += spltr.path -> InterpreterSplitter(spltr)
  def addGenerator(genr: AtomicGenerator[_]): Unit = _generators += genr.path -> InterpreterGenerator(genr)
  def addConnection(conn: AtomicConnection[_]): Unit = _connections += conn.path -> InterpreterConnection(conn)
