package pods.workflows

private[pods] object WorkflowRunner:
  def run(workflow: Workflow)(using system: SystemContext): Unit = 
    val nameAndRefs = workflow.tasks.map { (name, behavior) =>
      val (iref, oref): (IStreamRef[_], OStreamRef[_]) = TaskRunner.run(behavior)  
      (name, (iref, oref))
    }
    val nameAndRefsMap = nameAndRefs.toMap

    nameAndRefs.foreach { (name, refs) =>
      val hname = workflow.name + "/" + name
      system.registry.set(hname, refs.asInstanceOf)
    }

    workflow.connections.foreach { (from, to) =>
      val fromRef = nameAndRefsMap.get(from).get._2
      val toRef = nameAndRefsMap.get(to).get._1
      fromRef.subscribe(toRef.asInstanceOf)
    }

end WorkflowRunner