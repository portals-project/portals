package portals.runtime.local

import scala.collection.mutable.ArrayDeque
import scala.collection.mutable.Map

import portals.util.Common.Types.Path

private[portals] class AtomBuffers[T]:

  private val map: Map[Path, ArrayDeque[T]] = Map.empty

  def addOne(path: Path, event: T): Unit =
    map += path -> map.getOrElse(path, ArrayDeque[T]()).addOne(event)

  def removeHeadWhile(path: Path, p: T => Boolean): Seq[T] =
    map.get(path) match
      case Some(queue) => queue.removeHeadWhile(p)
      case None => Seq.empty

  def removeHeadWhileAll(p: T => Boolean): Seq[T] =
    map.flatMap { case (_, queue) => queue.removeHeadWhile(p) }.toSeq

  def removeHeadWhileAllAndOne(p: T => Boolean): Seq[T] =
    val r = map.flatMap { case (_, queue) => queue.removeHeadWhile(p) }.toSeq
    this.removeHead()
    r

  def removeHead(): Unit =
    map.foreach { case (_, queue) => queue.removeHead() }

  def removeHeadIfAll(p: T => Boolean): Seq[Path] =
    map.flatMap { case (path, queue) =>
      queue.headOption match
        case Some(head) =>
          if p(head) then
            queue.removeHead()
            Some(path)
          else None
        case _ => None
    }.toSeq

  def nonEmptyKeys(): Seq[Path] =
    map.filter { case (_, queue) => !queue.isEmpty }.keys.toSeq
