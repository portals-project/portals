package portals.system.test

class TestProgressTracker:
  // progress tracker for each Path;
  // for a Path (String) this gives the progress w.r.t. all input dependencies (Map[String, Long])
  private var progress: Map[String, Map[String, Long]] = Map.empty

  /** Set the progress of path and dependency to index. */
  def setProgress(path: String, dependency: String, idx: Long): Unit =
    progress +=
      path ->
        (progress.getOrElse(path, Map.empty) + (dependency -> idx))

  /** Increments the progress of path w.r.t. dependency by 1. If the dependency doesn't exist yet, then we set the new
    * index to 0.
    */
  def incrementProgress(path: String, dependency: String): Unit =
    progress +=
      path ->
        (progress.getOrElse(path, Map.empty) + (dependency -> (progress
          .getOrElse(path, Map.empty)
          .getOrElse(dependency, -1L) + 1)))

  /** Initialize the progress tracker for a certain path and dependency to -1. */
  def initProgress(path: String, dependency: String): Unit =
    progress += path -> (progress.getOrElse(path, Map.empty) + (dependency -> -1L))

  /** Get the current progress of the path and dependency. */
  def getProgress(path: String, dependency: String): Option[Long] =
    progress.get(path).flatMap(deps => deps.get(dependency))

  /** Get the current progress of the path and dependency. */
  def getProgress(path: String): Option[Map[String, Long]] =
    progress.get(path)
