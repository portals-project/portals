package pods.workflows

private[pods] trait RemoteIStreamRef[I]:
  private[pods] val resolvable: IStreamRef[I]

  def resolve(using SystemContext): IStreamRef[I] = resolvable
  def resolve(): IStreamRef[I] = resolvable

private[pods] object RemoteIStreamRef:
  def apply[I](iref: IStreamRef[I]): RemoteIStreamRef[I] = new RemoteIStreamRef[I] {
    override private[pods] val resolvable: IStreamRef[I] = iref
  }