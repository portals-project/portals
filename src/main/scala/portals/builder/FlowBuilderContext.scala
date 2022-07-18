package portals

trait FlowBuilderContext[T, U](using val wbctx: WorkflowBuilderContext[T, U]):
  val latest: Option[String] = None // immutable

object FlowBuilderContext:
  def apply[T, U](name: Option[String])(using WorkflowBuilderContext[T, U]) =
    new FlowBuilderContext[T, U] {
      override val latest: Option[String] = name
    }
