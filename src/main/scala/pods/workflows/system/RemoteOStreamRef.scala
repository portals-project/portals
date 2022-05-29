package pods.workflows

private[pods] trait RemoteOStreamRef[O]:
  private[pods] val resolvable: OStreamRef[O]

  def resolve(): OStreamRef[O] = resolvable

private[pods] object RemoteOStreamRef:
  def apply[O](oref: OStreamRef[O]): RemoteOStreamRef[O] = new RemoteOStreamRef[O] {
    override private[pods] val resolvable: OStreamRef[O] = oref
  }