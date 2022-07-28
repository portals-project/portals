package portals

trait FlowBuilderContext[T, U](using val wbctx: WorkflowBuilderContext[T, U]):
  // latest task that has been created, used for combinators
  val latest: Option[String] = None // immutable

object FlowBuilderContext:
  // creates a context with the name of the latest created task
  def apply[T, U](name: Option[String])(using WorkflowBuilderContext[T, U]) =
    new FlowBuilderContext[T, U] {
      override val latest: Option[String] = name
    }
