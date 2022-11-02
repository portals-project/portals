package portals.system.test

import portals.WrappedEvent

private[portals] sealed trait TestAtom
/** */
case class TestAtomBatch[T](path: String, list: List[WrappedEvent[T]]) extends TestAtom
// /** */
// case class TestPortalBatch[T](path: String, list: List[WrappedEvent[T]]) extends TestAtom
