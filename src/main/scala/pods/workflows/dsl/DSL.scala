package pods.workflows

object DSL:
  def ctx[I, O](using TaskContext[I, O]): TaskContext[I, O] = summon[TaskContext[I, O]]

  extension [T](ic: IStreamRef[T]) {
    def !(f: DSL.FUSE.type) = 
      ic.fuse()

    def ![I, O](event: T) = 
      ic.submit(event)
  }

  sealed trait Events
  case object FUSE extends Events

  object Utils:
    // launch a workflow that logs all inputs 
    def loggingWorkflow[T]()(using system: SystemContext): IStreamRef[T] =
      val builder = Workflows
        .builder()
        .withName("loggingWorkflow")
      
      val flow = builder
        .source[T]()
        .withName("source")
        .withLogger()
        .sink()

      val wf = builder.build()
      
      system.launch(wf)

      val iref = system.registry[T]("loggingWorkflow/source").resolve() 
      iref

      