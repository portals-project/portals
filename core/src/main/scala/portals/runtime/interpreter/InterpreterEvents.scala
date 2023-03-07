package portals.runtime.interpreter

import portals.runtime.WrappedEvents.*

object InterpreterEvents:
  /** Internal API. Atom of events together with meta data. */
  sealed trait InterpreterAtom

  /** Atom of regular events. */
  case class InterpreterAtomBatch[T](path: String, list: List[WrappedEvent[T]]) extends InterpreterAtom

  /** Atom of ask events. */
  private[portals] case class InterpreterPortalBatchMeta(
      portal: String,
      askingWF: String,
  )

  private[portals] case class InterpreterAskBatch[T](meta: InterpreterPortalBatchMeta, list: List[WrappedEvent[T]])
      extends InterpreterAtom

  /** Atom of reply events. */
  private[portals] case class InterpreterRepBatch[T](meta: InterpreterPortalBatchMeta, list: List[WrappedEvent[T]])
      extends InterpreterAtom
