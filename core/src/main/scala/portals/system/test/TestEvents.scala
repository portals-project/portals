package portals.system.test

import portals.*

private[portals] sealed trait TestAtom
private[portals] case class TestAtomBatch[T](path: String, list: List[WrappedEvent[T]]) extends TestAtom
// case class TestPortalBatch[T](path: String, list: List[WrappedEvent[T]]) extends TestAtom
