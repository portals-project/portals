package portals

/** Application Builder. */
trait Builder:
  def build(): Application

  def workflows[T, U](name: String): WorkflowBuilder[T, U]
end Builder // trait
