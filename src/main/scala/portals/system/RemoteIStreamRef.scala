package portals

private[portals] trait RemoteIStreamRef[I]:
  private[portals] val resolvable: IStreamRef[I]

  def resolve(using SystemContext): IStreamRef[I] = resolvable
  def resolve(): IStreamRef[I] = resolvable

private[portals] object RemoteIStreamRef:
  def apply[I](iref: IStreamRef[I]): RemoteIStreamRef[I] = new RemoteIStreamRef[I] {
    override private[portals] val resolvable: IStreamRef[I] = iref
  }