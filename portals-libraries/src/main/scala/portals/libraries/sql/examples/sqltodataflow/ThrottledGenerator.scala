package portals.libraries.sql.examples.sqltodataflow

import portals.application.generator.Generator
import portals.runtime.WrappedEvents.*
import portals.util.Key

/** ThrottledGenerator. Wrap a provided `generator`, and throttle its output to
  * at most `eventsPerSecond`.
  *
  * Note that the rate limiting is per partitioned generator instance.
  *
  * Note that the rate limiting is approximate. Once an atom has started
  * generating, it will not be interrupted, even in the case that the rate limit
  * is exceeded. That is, the last atom may exceed the rate limit since the last
  * threshold.
  *
  * @param generator
  *   the wrapped generator
  * @param eventsPerSecond
  *   the maximum number of events per second (approximate)
  * @tparam T
  *   the generated event type
  */
class ThrottledGenerator[T](generator: Generator[T], eventsPerSecond: Int) extends Generator[T]:
  private var eventCount = 0L
  private var timeOfLastRateLimit = 0L

  private def rateLimitExceeded(): Boolean =
    if eventCount < eventsPerSecond then //
      false
    else
      val now = System.currentTimeMillis()
      val elapsed = now - timeOfLastRateLimit
      if elapsed > 1000 then
        timeOfLastRateLimit = now
        eventCount = 0
        false
      else //
        true

  override def generate(): WrappedEvent[T] =
    generator.generate() match
      case e @ Event(key, value) =>
        eventCount += 1
        e
      case e @ _ =>
        e

  override def hasNext(): Boolean =
    !rateLimitExceeded() && generator.hasNext()
