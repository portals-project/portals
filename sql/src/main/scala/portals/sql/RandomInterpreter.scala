package portals.sql

import portals.runtime.interpreter.InterpreterRuntime

class RandomInterpreter(override val seed: Option[Int] = None) extends InterpreterRuntime(seed) {
  override def step(): Unit =
    /** Take a step. This will cause one of the processing entities (Workflows,
      * Sequencers, etc.) to process one atom and produce one (or more) atoms.
      * Throws an exception if it cannot take a step.
      */
    var choices = List[String]()
    if (choosePortal().isDefined) choices = choices :+ "portal"
    if (chooseWorkflow().isDefined) choices = choices :+ "workflow"
    if (chooseSequencer().isDefined) choices = choices :+ "sequencer"
    if (chooseSplitter().isDefined) choices = choices :+ "splitter"
    if (chooseGenerator().isDefined) choices = choices :+ "generator"
    if (choices.isEmpty) throw new Exception("No more steps")
    val choice = choices(rnd.nextInt(choices.size))
    choice match
      case "portal" =>
        val opt = choosePortal()
        stepPortal(opt.get._1, opt.get._2)
      case "workflow" =>
        val opt = chooseWorkflow()
        stepWorkflow(opt.get._1, opt.get._2)
      case "sequencer" =>
        val opt = chooseSequencer()
        stepSequencer(opt.get._1, opt.get._2)
      case "splitter" =>
        val opt = chooseSplitter()
        stepSplitter(opt.get._1, opt.get._2)
      case "generator" =>
        val opt = chooseGenerator()
        stepGenerator(opt.get._1, opt.get._2)
      case _ => throw new Exception("Unknown choice: " + choice)

    if stepN % GC_INTERVAL == 0 then garbageCollection()
}
