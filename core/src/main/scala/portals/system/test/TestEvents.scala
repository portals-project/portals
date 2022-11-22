package portals.system.test

import portals.*

/** Internal API. Atom of events together with meta data. */
private[portals] sealed trait TestAtom

/** Atom of regular events. */
private[portals] case class TestAtomBatch[T](path: String, list: List[WrappedEvent[T]]) extends TestAtom

// contain meta information about the receiving / responding workflows, and the corresponding portal :).

/** Atom of ask events. */
private[portals] case class TestAskBatch[T](portal: String, asker: String, replier: String, list: List[WrappedEvent[T]])
    extends TestAtom

/** Atom of reply events. */
private[portals] case class TestRepBatch[T](portal: String, asker: String, replier: String, list: List[WrappedEvent[T]])
    extends TestAtom
