package portals

private[portals] trait RemoteOStreamRef[O]:
  private[portals] val resolvable: OStreamRef[O]

  def resolve(): OStreamRef[O] = resolvable

private[portals] object RemoteOStreamRef:
  def apply[O](oref: OStreamRef[O]): RemoteOStreamRef[O] = new RemoteOStreamRef[O] {
    override private[portals] val resolvable: OStreamRef[O] = oref
  }