package pods.workflows

private[pods] trait ExecutionContext:
  def execute[I, O](processor: Processor[I, O]): (SubmitterProcessor[I, O], IStreamRef[I], OStreamRef[O])
  def shutdown(): Unit
