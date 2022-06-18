package pods.workflows

object Systems:
  def local(): SystemContext = new LocalSystem()

