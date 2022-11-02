package portals.system.test

import portals.Application

trait TestRuntimeContext:
  def streams: Map[String, TestStream]
  def processors: Map[String, TestProcessor]
  def generators: Map[String, TestGenerator]

class TestRuntimeContextImpl() extends TestRuntimeContext:
  var _streams: Map[String, TestStream] = Map.empty
  var _processors: Map[String, TestProcessor] = Map.empty
  var _generators: Map[String, TestGenerator] = Map.empty
  override def streams: Map[String, TestStream] = _streams
  override def processors: Map[String, TestProcessor] = _processors
  override def generators: Map[String, TestGenerator] = _generators

trait TestRuntime:
  /** Launch an application. */
  def launch(application: Application): Unit

  /** Take a step. This will cause one of the processing entities (Workflows, Sequencers, etc.) to process one atom and
    * produce one (or more) atoms. Throws an exception if it cannot take a step.
    */
  def step(): Unit

  /** Takes steps until it cannot take more steps. */
  def stepUntilComplete(): Unit

  /** If the runtime can take another step, returns true if it can process something. It returns false if it has
    * finished processing, i.e. all atomic streams have been read.
    */
  def canStep(): Boolean

  /** Terminate the runtime. */
  def shutdown(): Unit

class TestRuntimeImpl extends TestRuntime:
  val rctx = new TestRuntimeContextImpl()

  override def launch(application: Application): Unit =
    // launch streams
    application.streams.foreach { stream =>
      rctx._streams += stream.path -> TestAtomicStream()(using rctx)
    }

    // launch workflows
    application.workflows.foreach { wf =>
      rctx._processors += wf.path -> TestWorkflow()(wf)(using rctx)
    }

    // launch sequencers
    application.sequencers.foreach { seqr =>
      rctx._processors += seqr.path -> TestSequencer()(seqr)(using rctx)
    }

    // launch generators
    application.generators.foreach { genr =>
      rctx._generators += genr.path -> TestGenerator()(genr)(using rctx)
    }

  override def step(): Unit =
    // find candidate to take a step
    rctx._processors.find((path, proc) => proc.hasInput()) match
      case Some((path, proc)) =>
        val inputp = proc.inputs.find((path, idx) => idx < rctx.streams(path).getIdxRange()._2).get
        val atom = rctx.streams(inputp._1).read(inputp._2)
        val atoms = proc.process(atom)
        proc._inputs += inputp._1 -> (proc._inputs(inputp._1) + 1)
        atoms.foreach(atom =>
          atom match
            case ta @ TestAtomBatch(path, list) => rctx._streams(path).enqueue(ta)
            case TestPortalBatch(path, list) => ??? // not yet
        )
      case None =>
        rctx._generators.find((path, gen) => gen.hasInput()) match
          case Some((path, gen)) =>
            val outputs = gen.process()
            outputs.foreach(atom =>
              atom match
                case ta @ TestAtomBatch(path, list) =>
                  rctx._streams(path).enqueue(ta)
                case TestPortalBatch(path, list) => ??? // not yet
            )
          case None => ??? // shouldn't happen if you check canStep first :)

  override def stepUntilComplete(): Unit =
    while this.canStep() do
      step()

  override def canStep(): Boolean =
    rctx.processors.exists((path, proc) => proc.hasInput())
      || rctx._generators.exists((path, proc) => proc.hasInput())

  override def shutdown(): Unit = () // do nothing :)
