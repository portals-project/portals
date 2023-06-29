package portals.runtime.interpreter.trackers

import portals.util.Common.Types.Path

class SuspendingTracker:
  private var _suspended: Set[Path] = Set.empty

  def isSuspended(path: Path): Boolean = _suspended.contains(path)

  def add(path: Path): Unit = _suspended += path

  def remove(path: Path): Unit = _suspended -= path
