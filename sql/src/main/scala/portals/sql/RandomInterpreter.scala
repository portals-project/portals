package portals.sql

import portals.runtime.WrappedEvents.WrappedEvent
import portals.runtime.interpreter.InterpreterEvents.{InterpreterAskBatch, InterpreterAtom, InterpreterPortalBatchMeta, InterpreterRepBatch}
import portals.runtime.interpreter.{InterpreterPortal, InterpreterRuntime}

class RandomInterpreter(override val seed: Option[Int] = None) extends InterpreterRuntime(seed) {
  override def step(): Unit =
    /** Take a step. This will cause one of the processing entities (Workflows,
      * Sequencers, etc.) to process one atom and produce one (or more) atoms.
      * Throws an exception if it cannot take a step.
      */
//    var choices = List[String]()
//    if (choosePortal().isDefined) choices = choices :+ "portal"
//    if (chooseWorkflow().isDefined) choices = choices :+ "workflow"
//    if (chooseSequencer().isDefined) choices = choices :+ "sequencer"
//    if (chooseSplitter().isDefined) choices = choices :+ "splitter"
//    if (chooseGenerator().isDefined) choices = choices :+ "generator"
//    if (choices.isEmpty) throw new Exception("No more steps")
//    val choice = choices(rnd.nextInt(choices.size))
//    choice match
//      case "portal" =>
//        val opt = choosePortal()
//        stepPortal(opt.get._1, opt.get._2)
//      case "workflow" =>
//        val opt = chooseWorkflow()
//        stepWorkflow(opt.get._1, opt.get._2)
//      case "sequencer" =>
//        val opt = chooseSequencer()
//        stepSequencer(opt.get._1, opt.get._2)
//      case "splitter" =>
//        val opt = chooseSplitter()
//        stepSplitter(opt.get._1, opt.get._2)
//      case "generator" =>
//        val opt = chooseGenerator()
//        stepGenerator(opt.get._1, opt.get._2)
//      case _ => throw new Exception("Unknown choice: " + choice)
//
//    if stepN % GC_INTERVAL == 0 then garbageCollection()

    chooseGenerator() match
      case Some(path, portal) => stepGenerator(path, portal)
      case None =>
        chooseWorkflow() match
          case Some(path, wf) => stepWorkflow(path, wf)
          case None =>
            chooseSequencer() match
              case Some(path, seqr) => stepSequencer(path, seqr)
              case None =>
                chooseSplitter() match
                  case Some(path, spltr) => stepSplitter(path, spltr)
                  case None =>
                    choosePortal() match
                      case Some(path, genr) =>
                        stepPortal(path, genr)
                      case None => ???
    if stepN % GC_INTERVAL == 0 then garbageCollection()

  case class InterpreterPortalEvent(meta: InterpreterPortalBatchMeta, list: List[WrappedEvent[_]], typ: String) {
    def toInterpreterAtom: InterpreterAtom =
      typ match
        case "ask" => InterpreterAskBatch(meta, list)
        case "rep" => InterpreterRepBatch(meta, list)
        case _ => throw new Exception("Unknown InterpreterPortalEvent: " + this)
  }

  def fromInterpreterAtom(atom: InterpreterAtom): InterpreterPortalEvent =
    atom match
      case InterpreterAskBatch(meta, list) => InterpreterPortalEvent(meta, list, "ask")
      case InterpreterRepBatch(meta, list) => InterpreterPortalEvent(meta, list, "rep")
      case _ => throw new Exception("Unknown InterpreterAtom: " + atom)

  override def stepPortal(path: String, portal: InterpreterPortal): Unit = {
// NOTE: seems no need to shuffle
//    // drain the portal, get event list
//    var events = List[InterpreterAtom]()
//    while !portal.isEmpty do events = events :+ portal.dequeue().get
//    val portalEvents = events.map(fromInterpreterAtom)
//    
//    
//    val shuffledList = {
//      var grouped = portalEvents.groupBy(_.meta.askingWF).values.toList
//
//      var result = List[InterpreterPortalEvent]()
//      // randomly choose one group, pop an element from its head, put into result
//      // if the group is empty, remove it from the list
//      // repeat until the list is empty
//      while (grouped.nonEmpty) {
//        val groupChoice = rnd.nextInt(grouped.size)
//        val group = grouped(groupChoice)
//        val (head, tail) = group.splitAt(1)
//        result = result :+ head.head
//        grouped = grouped.updated(groupChoice, tail)
//        grouped = grouped.filter(_.nonEmpty)
//      }
//      result.map(_.toInterpreterAtom)
//    }
//    
//    shuffledList.foreach(portal.enqueue)
//    
//    if shuffledList != events then
//      println("Shuffled list: " + shuffledList)
//      println("Original list: " + events)

    super.stepPortal(path, portal)
  }
}
