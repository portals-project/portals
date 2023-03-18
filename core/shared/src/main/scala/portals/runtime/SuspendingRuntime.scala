package portals.runtime

import portals.application.Application
import portals.runtime.BatchedEvents.*
import portals.util.Common.Types.Path

object SuspendingRuntime:
  /** Resulting output produced by taking a `step()`. */
  sealed trait StepResult

  /** Complete `output` from processing an atom to completion. */
  case class Completed(
      path: Path,
      outputs: List[EventBatch]
  ) extends StepResult

  /** Suspended processing of `path` with `shuffle` side-effects and
    * `intermediate` output.
    */
  case class Suspended(
      path: Path,
      shuffle: List[ShuffleBatch[_]],
      intermediate: List[EventBatch],
  ) extends StepResult

trait SuspendingRuntime extends PortalsRuntime:
  import SuspendingRuntime.*

  /** Returns true if the runtime can take another step. */
  def canStep(): Boolean

  /** Take and return the result of a step.
    *
    * Returns `Completed` if the step processes an atom until completion.
    *
    * Returns `Suspended` if the processing was suspended, for example due to a
    * `ShuffleTask`. A suspended processor will not be able to take another step
    * until it is `resume()`d. A suspended processor is resumed through the call
    * `resume()` with the corresponding `path` and `shuffle` side-effects.
    */
  def step(): StepResult

  /** Resume a suspended processor for `path` and input `shuffles`. */
  def resume(path: Path, shuffles: List[ShuffleBatch[_]]): StepResult

  /** Feed a `listOfAtoms` to the runtime.
    *
    * Distributes the atoms to the corresponding processing units. Can be used
    * to feed back the `outputs` from `Completed` steps.
    */
  def feedAtoms(listOfAtoms: List[EventBatch]): Unit

end SuspendingRuntime
