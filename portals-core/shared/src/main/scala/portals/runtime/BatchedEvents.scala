package portals.runtime

import portals.runtime.WrappedEvents.*
import portals.util.Common.Types.Path

object BatchedEvents:
  /** Internal API. Atom of events together with meta data. */
  sealed trait EventBatch

  /** Atom of regular events. */
  case class AtomBatch[T](path: String, list: List[WrappedEvent[T]]) extends EventBatch

  /** Metadata for ask and reply events. */
  private[portals] case class AskReplyMeta(
      portal: Path,
      askingWF: String,
      replyingWF: Option[String] = None,
      replyingTask: Option[String] = None,
  )

  /** Atom of ask events. */
  private[portals] case class AskBatch[T](meta: AskReplyMeta, list: List[WrappedEvent[T]]) extends EventBatch

  /** Atom of reply events. */
  private[portals] case class ReplyBatch[T](meta: AskReplyMeta, list: List[WrappedEvent[T]]) extends EventBatch

  /** Events that are shuffled due to a key change. */
  private[portals] case class ShuffleBatch[T](path: Path, task: Path, list: List[WrappedEvent[T]]) extends EventBatch

  extension (batch: EventBatch) {
    def list: List[WrappedEvent[_]] = batch match
      case AtomBatch(_, list) => list
      case AskBatch(_, list) => list
      case ReplyBatch(_, list) => list
      case ShuffleBatch(_, _, list) => list

    def nonEmpty: Boolean = batch match
      case AtomBatch(_, list) => list.nonEmpty
      case AskBatch(_, list) => list.nonEmpty
      case ReplyBatch(_, list) => list.nonEmpty
      case ShuffleBatch(_, _, list) => list.nonEmpty
  }
