package portals.system.test

import portals.*

private[portals] sealed trait TestAtom
private[portals] case class TestAtomBatch[T](path: String, list: List[WrappedEvent[T]]) extends TestAtom
private[portals] case class TestPortalAskBatch[T](sendr: String, recvr: String, list: List[WrappedEvent[T]])
    extends TestAtom
private[portals] case class TestPortalRepBatch[T](sendr: String, recvr: String, list: List[WrappedEvent[T]])
    extends TestAtom
