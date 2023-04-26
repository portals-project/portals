package portals.runtime.executor

import scala.collection.immutable.VectorBuilder

import portals.application.generator.Generator
import portals.runtime.WrappedEvents.*

/** Internal API. Executor for Generators. */
private[portals] class GeneratorExecutorImpl[T]:

  // setup
  private var generator: Generator[T] = _
  private var path: String = _

  // internal executor state
  private val vectorBuilder = VectorBuilder[portals.runtime.WrappedEvents.WrappedEvent[T]]()
  var atom = false
  var error = false
  var seal = false
  private var clear_next_time = false
  private def go = !atom && !error && !seal

  /** Setup the generator executor with `_path` for `_generator`. */
  def setup(_path: String, _generator: Generator[T]): Unit =
    generator = _generator
    path = _path

  /** Reset execution state before a run. */
  private def reset_before_run(): Unit =
    atom = false
    error = false
    seal = false
    if clear_next_time then
      vectorBuilder.clear()
      clear_next_time = false

  /** Run the generator until some atom Some(Atom) or not finished (None). The
    * final execution state can be inspected by checking the variables `atom`,
    * `error`, `seal`.
    */
  def run(): Option[Vector[WrappedEvent[T]]] =
    // reset
    reset_before_run()

    // build vector
    while go && generator.hasNext() do
      generator.generate() match
        case portals.runtime.WrappedEvents.Event(key, event) =>
          vectorBuilder += portals.runtime.WrappedEvents.Event(key, event)
        case portals.runtime.WrappedEvents.Atom =>
          vectorBuilder += portals.runtime.WrappedEvents.Atom
          atom = true
        case portals.runtime.WrappedEvents.Seal =>
          vectorBuilder.clear()
          vectorBuilder += portals.runtime.WrappedEvents.Seal
          seal = true
        case portals.runtime.WrappedEvents.Error(t) =>
          vectorBuilder.clear()
          vectorBuilder += portals.runtime.WrappedEvents.Error(t)
          error = true
        case _ => ???

    // return vector if atom, error, or seal
    if atom || error || seal then
      clear_next_time = true
      Some(vectorBuilder.result())
    else None
