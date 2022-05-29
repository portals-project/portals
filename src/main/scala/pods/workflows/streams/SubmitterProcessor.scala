package pods.workflows

trait SubmitterProcessor[I, O] extends Processor[I, O] with Submitter[O]